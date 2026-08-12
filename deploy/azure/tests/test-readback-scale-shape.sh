#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
TMP=$(mktemp -d /tmp/rotrack-azure-readback-test.XXXXXX)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
BIN="$TMP/bin"
mkdir -p "$BIN"
python3 - "$TMP/foundation.json" "$TMP/app.json" "$TMP/dates.env" <<'PY'
import json
import pathlib
from datetime import datetime, timezone
import sys
now = datetime.now(timezone.utc)
start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
end = start.replace(year=start.year + 1)
digest = 'sha256:' + 'a' * 64
foundation = {'parameters': {
    'location': {'value': 'eastus'}, 'acrName': {'value': 'rotracknonproductionabc123'},
    'budgetAmount': {'value': 15}, 'budgetStartDate': {'value': start.strftime('%Y-%m-%dT00:00:00Z')},
    'budgetEndDate': {'value': end.strftime('%Y-%m-%dT00:00:00Z')}, 'budgetAlertEmails': {'value': ['owner@example.test']},
}}
app = {'parameters': {
    'location': {'value': 'eastus'}, 'acrName': {'value': 'rotracknonproductionabc123'},
    'imageRepository': {'value': 'rotrack-api'}, 'imageDigest': {'value': digest},
    'databaseUrl': {'value': 'jdbc:postgresql://abcdefghijklmnopqrst.supabase.co:5432/postgres?sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt'},
    'databaseUsername': {'value': 'rotrack_runtime'}, 'databasePassword': {'value': 'test-password'},
    'databaseCaCertificatePem': {'value': '-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----'},
    'supabaseJwksUri': {'value': 'https://abcdefghijklmnopqrst.supabase.co/auth/v1/.well-known/jwks.json'},
    'supabaseIssuerUri': {'value': 'https://abcdefghijklmnopqrst.supabase.co/auth/v1'},
    'corsAllowedOrigins': {'value': 'https://preview.vercel.app'},
}}
pathlib.Path(sys.argv[1]).write_text(json.dumps(foundation), encoding='utf-8')
pathlib.Path(sys.argv[2]).write_text(json.dumps(app), encoding='utf-8')
pathlib.Path(sys.argv[3]).write_text(f'DIGEST={digest}\nSTART={start.strftime("%Y-%m-%dT00:00:00Z")}\nEND={end.strftime("%Y-%m-%dT00:00:00Z")}\n', encoding='utf-8')
PY
chmod 400 "$TMP/foundation.json"
chmod 600 "$TMP/app.json"
. "$TMP/dates.env"
export DIGEST START END
cat > "$BIN/az" <<'SH'
#!/bin/sh
set -eu
ARGS="$*"
case "$ARGS" in
  *'account show'*) printf '%s\n' "$AZURE_SUBSCRIPTION_ID" ;;
  *'group show'*) printf '%s\n' eastus ;;
  *'acr show'*'--query id'*) printf '%s\n' '/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' ;;
  *'acr show'*'loginServer'*) printf '%s\n' rotracknonproductionabc123.azurecr.io ;;
  *'acr show'*) printf '%s\n' '{"name":"rotracknonproductionabc123","sku":"Basic","adminUserEnabled":false}' ;;
  *'resource show'*) printf '%s\n' '{"name":"rotrack-nonproduction-logs","sku":"PerGB2018","retentionInDays":30,"dailyQuotaGb":0.1}' ;;
  *'containerapp env show'*) printf '%s\n' rotrack-nonproduction-env ;;
  *'identity show'*) printf '%s\n' fake-principal-id ;;
  *'role assignment list'*) printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]' ;;
  *'rest --method get'*) printf '%s\n' "{\"amount\":\"15.0\",\"timePeriod\":{\"startDate\":\"$START\",\"endDate\":\"$END\"},\"notifications\":{\"actual50\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":50,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual80\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":80,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual100\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":100,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]}}}" ;;
  *'containerapp show'*)
    python3 - <<'PY'
import json, os
mode = os.environ.get('FAKE_TRAFFIC_MODE', 'latest')
traffic = [{'latestRevision': True, 'weight': 100}] if mode == 'latest' else [{'revisionName': 'rotrack-api--prior', 'weight': 100}]
print(json.dumps({'name':'rotrack-api-nonproduction','latestRevisionName':'rotrack-api--revision','activeRevisionsMode':'Multiple','fqdn':'rotrack-api.example.azurecontainerapps.io','scale':{'minReplicas':1,'maxReplicas':1,'cooldownPeriod':300,'pollingInterval':30,'rules':[]},'terminationGracePeriodSeconds':30,'ingress':{'external':True,'targetPort':8080,'allowInsecure':False,'traffic':traffic},'probes':[{'type':'Liveness','httpGet':{'path':'/api/v1/health'}},{'type':'Readiness','httpGet':{'path':'/api/v1/readiness'}}]}))
PY
    ;;
  *'containerapp revision show'*)
    case "$FAKE_TRAFFIC_MODE:$ARGS" in
      latest:*'--revision rotrack-api--revision'*) ;;
      prior:*'--revision rotrack-api--prior'*) ;;
      *) printf '%s\n' 'readback queried the wrong selected revision' >&2; exit 1 ;;
    esac
    python3 - <<'PY'
import json, os
names = [
'DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD','DATABASE_CA_CERTIFICATE_PEM','SUPABASE_JWKS_URI','SUPABASE_ISSUER_URI',
'PORT','DATABASE_CA_CERTIFICATE_PATH','DATABASE_CONNECTION_TIMEOUT_MS','DATABASE_POOL_VALIDATION_TIMEOUT_MS','DATABASE_MAXIMUM_POOL_SIZE','DATABASE_MINIMUM_IDLE','READINESS_CACHE_TTL','SUPABASE_JWT_AUDIENCE','CORS_ALLOWED_ORIGINS','ROTRACK_MUTATION_RATE_LIMIT_REQUESTS','ROTRACK_MUTATION_RATE_LIMIT_WINDOW','ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS','SERVER_SHUTDOWN','SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE','LOGGING_STRUCTURED_FORMAT_CONSOLE','ROTRACK_STRUCTURED_LOGGING_ENABLED','ROTRACK_LOGGING_ENVIRONMENT','ROTRACK_SERVICE_VERSION','LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY','LOGGING_LEVEL_ORG_HIBERNATE_SQL','LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND','SPRING_JPA_SHOW_SQL']
secrets = {'DATABASE_URL':'database-url','DATABASE_USERNAME':'database-username','DATABASE_PASSWORD':'database-password','DATABASE_CA_CERTIFICATE_PEM':'database-ca-certificate-pem','SUPABASE_JWKS_URI':'supabase-jwks-uri','SUPABASE_ISSUER_URI':'supabase-issuer-uri'}
values = {'PORT':'8080','DATABASE_CA_CERTIFICATE_PATH':'/tmp/rotrack-certs/supabase-db-ca.crt','DATABASE_CONNECTION_TIMEOUT_MS':'5000','DATABASE_POOL_VALIDATION_TIMEOUT_MS':'2000','DATABASE_MAXIMUM_POOL_SIZE':'5','DATABASE_MINIMUM_IDLE':'0','READINESS_CACHE_TTL':'5s','SUPABASE_JWT_AUDIENCE':'authenticated','CORS_ALLOWED_ORIGINS':'https://preview.vercel.app','ROTRACK_MUTATION_RATE_LIMIT_REQUESTS':'30','ROTRACK_MUTATION_RATE_LIMIT_WINDOW':'1m','ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS':'10000','SERVER_SHUTDOWN':'graceful','SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE':'25s','LOGGING_STRUCTURED_FORMAT_CONSOLE':'ecs','ROTRACK_STRUCTURED_LOGGING_ENABLED':'true','ROTRACK_LOGGING_ENVIRONMENT':'production','ROTRACK_SERVICE_VERSION':os.environ.get('REVISION_DIGEST', os.environ['DIGEST']),'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY':'WARN','LOGGING_LEVEL_ORG_HIBERNATE_SQL':'OFF','LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND':'OFF','SPRING_JPA_SHOW_SQL':'false'}
secret_value = os.environ.get('FAKE_SECRET_VALUE', '')
env = [{'name': n, 'secretRef': secrets[n], 'value': secret_value} if n in secrets else {'name': n, 'value': values[n]} for n in names]
revision = 'rotrack-api--prior' if os.environ.get('FAKE_TRAFFIC_MODE') == 'prior' else 'rotrack-api--revision'
image_digest = os.environ.get('REVISION_DIGEST', os.environ['DIGEST'])
print(json.dumps({'name':revision,'image':'rotracknonproductionabc123.azurecr.io/rotrack-api@'+image_digest,'env':env}))
PY
    ;;
esac
SH
chmod +x "$BIN/az"

FAKE_TRAFFIC_MODE=latest \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" AZURE_APP_PARAMETER_FILE="$TMP/app.json" \
  PATH="$BIN:$PATH" "$ROOT/scripts/azure/readback.sh"
FAKE_TRAFFIC_MODE=prior \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" AZURE_APP_PARAMETER_FILE="$TMP/app.json" \
  PATH="$BIN:$PATH" "$ROOT/scripts/azure/readback.sh" >/dev/null
if FAKE_TRAFFIC_MODE=prior REVISION_DIGEST=sha256:$(printf '%064d' 0) \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" AZURE_APP_PARAMETER_FILE="$TMP/app.json" \
  PATH="$BIN:$PATH" "$ROOT/scripts/azure/readback.sh" >/dev/null 2>&1; then
  printf '%s\n' 'azure readback accepted a mismatched selected revision' >&2
  exit 1
fi
if FAKE_SECRET_VALUE=unexpected-secret-value \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" AZURE_APP_PARAMETER_FILE="$TMP/app.json" \
  PATH="$BIN:$PATH" "$ROOT/scripts/azure/readback.sh" >/dev/null 2>&1; then
  printf '%s\n' 'azure readback accepted a non-empty secret value alongside secretRef' >&2
  exit 1
fi
printf '%s\n' 'azure readback selected-revision contract: passed'
