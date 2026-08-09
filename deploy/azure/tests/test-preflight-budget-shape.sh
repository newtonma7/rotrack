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
case "$ARGS" in
  *'account show'*) printf '%s\n' "$AZURE_SUBSCRIPTION_ID" ;;
  *'group show'*) printf '%s\n' eastus ;;
  *'acr show'*'--query id'*) printf '%s\n' '/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' ;;
  *'acr show'*) printf '%s\n' '{"name":"rotracknonproductionabc123","sku":"Basic","adminUserEnabled":false}' ;;
  *'resource show'*) printf '%s\n' '{"name":"rotrack-nonproduction-logs","sku":"PerGB2018","retentionInDays":30,"dailyQuotaGb":0.1}' ;;
  *'containerapp env show'*) printf '%s\n' rotrack-nonproduction-env ;;
  *'identity show'*) printf '%s\n' fake-principal-id ;;
  *'role assignment list'*) printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]' ;;
  *'consumption budget show'*'properties.amount'*) printf '%s\n' '{"amount":null,"start":null,"end":null,"notifications":{}}' ;;
  *'consumption budget show'*) printf '%s\n' "{\"amount\":15,\"timePeriod\":{\"startDate\":\"$FAKE_START\",\"endDate\":\"$FAKE_END\"},\"notifications\":{\"actual50\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":50,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual80\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":80,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual100\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":100,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]}}}" ;;
esac
SH
chmod +x "$BIN/az"

AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" \
PATH="$BIN:$PATH" \
  "$ROOT/scripts/azure/preflight.sh"

printf '%s\n' 'azure preflight budget response-shape contract: passed'
