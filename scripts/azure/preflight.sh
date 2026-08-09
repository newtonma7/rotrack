#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

require_command az
require_subscription
require_target
validate_foundation_parameters

ACCOUNT_ID=$(az account show --subscription "$AZURE_SUBSCRIPTION_ID" --query id --output tsv)
[ "$ACCOUNT_ID" = "$AZURE_SUBSCRIPTION_ID" ] || fail 'selected subscription did not match AZURE_SUBSCRIPTION_ID'

GROUP_LOCATION=$(az group show \
  --name rotrack-nonproduction \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query location \
  --output tsv) || fail 'non-production resource group does not exist'
[ "$GROUP_LOCATION" = "$FOUNDATION_LOCATION" ] || fail 'resource group region does not match foundation parameters'

ACR_JSON=$(az acr show \
  --name "$FOUNDATION_ACR_NAME" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query '{name:name,sku:sku.name,adminUserEnabled:adminUserEnabled}' \
  --output json)
export ACR_JSON
python3 - <<'PY'
import json
import os
acr = json.loads(os.environ['ACR_JSON'])
if acr.get('name') is None or acr.get('sku') != 'Basic' or acr.get('adminUserEnabled') is not False:
    raise SystemExit('ACR foundation contract is not satisfied')
PY

WORKSPACE_JSON=$(az resource show \
  --resource-group rotrack-nonproduction \
  --resource-type Microsoft.OperationalInsights/workspaces \
  --name rotrack-nonproduction-logs \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query '{name:name,sku:properties.sku.name,retentionInDays:properties.retentionInDays,dailyQuotaGb:properties.workspaceCapping.dailyQuotaGb}' \
  --output json)
export WORKSPACE_JSON
python3 - <<'PY'
import json
import os
workspace = json.loads(os.environ['WORKSPACE_JSON'])
if workspace.get('name') != 'rotrack-nonproduction-logs' or workspace.get('sku') != 'PerGB2018' or workspace.get('retentionInDays') != 30 or workspace.get('dailyQuotaGb') != 0.1:
    raise SystemExit('Log Analytics retention/cap contract is not satisfied')
PY

az containerapp env show \
  --name rotrack-nonproduction-env \
  --resource-group rotrack-nonproduction \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query name \
  --output tsv >/dev/null

if [ "${AZURE_WAIT_FOR_RBAC:-0}" = 1 ]; then
  wait_for_acr_pull
else
  PRINCIPAL_ID=$(az identity show \
    --name rotrack-api-nonproduction-identity \
    --resource-group rotrack-nonproduction \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --query principalId \
    --output tsv)
  [ -n "$PRINCIPAL_ID" ] || fail 'managed identity principal readback was empty'
  ROLE_COUNT=$(az role assignment list \
    --assignee-object-id "$PRINCIPAL_ID" \
    --role AcrPull \
    --all \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --query "[?contains(scope, '/registries/$FOUNDATION_ACR_NAME')]|length(@)" \
    --output tsv)
  [ "$ROLE_COUNT" -ge 1 ] || fail 'managed identity does not have AcrPull on the target ACR'
fi

BUDGET_JSON=$(az consumption budget show \
  --budget-name rotrack-nonproduction-budget \
  --resource-group rotrack-nonproduction \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query '{amount:properties.amount,start:properties.timePeriod.startDate,end:properties.timePeriod.endDate,notifications:properties.notifications}' \
  --output json)
export BUDGET_JSON
export FOUNDATION_PARAMETER_FILE
python3 - <<'PY'
import json
import os
from datetime import datetime, timezone

budget = json.loads(os.environ['BUDGET_JSON'])
params = json.load(open(os.environ['FOUNDATION_PARAMETER_FILE'], encoding='utf-8'))['parameters']
expected_amount = params['budgetAmount']['value']
expected_start = params['budgetStartDate']['value']
expected_end = params['budgetEndDate']['value']
if budget.get('amount') != expected_amount or budget.get('start') != expected_start or budget.get('end') != expected_end:
    raise SystemExit('budget amount or bounded period does not match foundation parameters')
start = datetime.strptime(budget['start'], '%Y-%m-%dT00:00:00Z')
end = datetime.strptime(budget['end'], '%Y-%m-%dT00:00:00Z')
now = datetime.now(timezone.utc).replace(tzinfo=None)
if not 1 <= (end - start).days <= 366 or not (start <= now < end):
    raise SystemExit('budget period is not current and bounded')
notifications = budget.get('notifications', {})
expected_emails = set(params['budgetAlertEmails']['value'])
for key, threshold in (('actual50', 50), ('actual80', 80), ('actual100', 100)):
    item = notifications.get(key, {})
    if item.get('enabled') is not True or item.get('operator') != 'GreaterThan' or item.get('threshold') != threshold or item.get('thresholdType') != 'Actual' or set(item.get('contactEmails', [])) != expected_emails:
        raise SystemExit(f'missing actual budget alert: {key}')
PY

printf '%s\n' 'azure preflight: foundation exists and redacted contract readback passed'
