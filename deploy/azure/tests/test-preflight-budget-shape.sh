#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
TMP=$(mktemp -d /tmp/rotrack-azure-budget-shape.XXXXXX)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
BIN="$TMP/bin"
mkdir -p "$BIN"
python3 - "$TMP/foundation.json" "$TMP/dates.env" <<'PY'
import json
import pathlib
from datetime import datetime, timezone
import sys
now = datetime.now(timezone.utc)
start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
end = start.replace(year=start.year + 1)
params = {
    '$schema': 'https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#',
    'contentVersion': '1.0.0.0',
    'parameters': {
        'location': {'value': 'eastus'},
        'acrName': {'value': 'rotracknonproductionabc123'},
        'budgetAmount': {'value': 15},
        'budgetStartDate': {'value': start.strftime('%Y-%m-%dT00:00:00Z')},
        'budgetEndDate': {'value': end.strftime('%Y-%m-%dT00:00:00Z')},
        'budgetAlertEmails': {'value': ['owner@example.test']},
    },
}
pathlib.Path(sys.argv[1]).write_text(json.dumps(params), encoding='utf-8')
pathlib.Path(sys.argv[2]).write_text(f'FAKE_START={start.strftime("%Y-%m-%dT00:00:00Z")}\nFAKE_END={end.strftime("%Y-%m-%dT00:00:00Z")}\n', encoding='utf-8')
PY
chmod 400 "$TMP/foundation.json"
. "$TMP/dates.env"
export FAKE_START FAKE_END
cat > "$BIN/az" <<'SH'
#!/bin/sh
set -eu
ARGS="$*"
printf '%s\n' "$ARGS" >> "$FAKE_AZ_LOG"
case "$ARGS" in
  *'account show'*) printf '%s\n' "$AZURE_SUBSCRIPTION_ID" ;;
  *'group show'*) printf '%s\n' eastus ;;
  *'acr show'*'--query id'*) printf '%s\n' '/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' ;;
  *'acr show'*) printf '%s\n' '{"name":"rotracknonproductionabc123","sku":"Basic","adminUserEnabled":false}' ;;
  *'resource show'*) printf '%s\n' '{"name":"rotrack-nonproduction-logs","sku":"PerGB2018","retentionInDays":30,"dailyQuotaGb":0.1}' ;;
  *'containerapp env show'*) printf '%s\n' rotrack-nonproduction-env ;;
  *'identity show'*) printf '%s\n' fake-principal-id ;;
  *'role assignment list'*) printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]' ;;
  *'consumption budget show'*) printf '%s\n' '{}' ;;
  *'rest'*'Microsoft.Consumption/budgets/rotrack-nonproduction-budget'*'api-version=2023-05-01'*)
    FAKE_START="$FAKE_START" FAKE_END="$FAKE_END" FAKE_OMIT_THRESHOLD_TYPE="${FAKE_OMIT_THRESHOLD_TYPE:-0}" python3 - <<'PY'
import json
import os
notifications = {}
for name, threshold in (('actual50', '50.0'), ('actual80', '80.0'), ('actual100', '100.0')):
    item = {
        'enabled': True,
        'operator': 'GreaterThan',
        'threshold': threshold,
        'contactEmails': ['owner@example.test'],
    }
    if os.environ['FAKE_OMIT_THRESHOLD_TYPE'] != '1':
        item['thresholdType'] = 'Actual'
    notifications[name] = item
print(json.dumps({'amount': '15.0', 'timePeriod': {'startDate': os.environ['FAKE_START'], 'endDate': os.environ['FAKE_END']}, 'notifications': notifications}))
PY
    ;;
esac
SH
chmod +x "$BIN/az"

AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_NONPRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" \
FAKE_AZ_LOG="$TMP/az.log" \
PATH="$BIN:$PATH" \
  "$ROOT/scripts/azure/preflight.sh"
grep -Fq -- 'rest --method get --url https://management.azure.com/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rotrack-nonproduction/providers/Microsoft.Consumption/budgets/rotrack-nonproduction-budget?api-version=2023-05-01' "$TMP/az.log"
grep -Fq -- '--query properties' "$TMP/az.log"
if AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_NONPRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" \
  FAKE_AZ_LOG="$TMP/az-negative.log" FAKE_OMIT_THRESHOLD_TYPE=1 \
  PATH="$BIN:$PATH" "$ROOT/scripts/azure/preflight.sh" >/dev/null 2>&1; then
  printf '%s\n' 'preflight accepted a REST budget response without thresholdType' >&2
  exit 1
fi

printf '%s\n' 'azure preflight budget REST response-shape contract: passed'
