#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
AZURE_DIR="$ROOT/deploy/azure/production"
command -v az >/dev/null 2>&1 || { printf '%s\n' 'az is required for production Bicep validation' >&2; exit 2; }

foundation_rendered=$(mktemp)
app_rendered=$(mktemp)
trap 'rm -f "$foundation_rendered" "$app_rendered"' EXIT HUP INT TERM
az bicep build --file "$AZURE_DIR/foundation.bicep" --outfile "$foundation_rendered" >/dev/null
az bicep build --file "$AZURE_DIR/app.bicep" --outfile "$app_rendered" >/dev/null
python3 - "$foundation_rendered" "$app_rendered" <<'PY'
import json
import pathlib
import sys

foundation, app = [json.loads(pathlib.Path(path).read_text(encoding='utf-8')) for path in sys.argv[1:]]
for template in (foundation, app):
    serialized = json.dumps(template)
    assert 'rotrack-production' in serialized
    assert 'rotrack-nonproduction' not in serialized
    assert 'arn:aws' not in serialized
    assert 'AWS::' not in serialized
foundation_types = {resource['type'] for resource in foundation['resources']}
assert foundation_types == {
    'Microsoft.OperationalInsights/workspaces',
    'Microsoft.ContainerRegistry/registries',
    'Microsoft.ManagedIdentity/userAssignedIdentities',
    'Microsoft.Authorization/roleAssignments',
    'Microsoft.App/managedEnvironments',
    'Microsoft.Consumption/budgets',
}
app_resources = app['resources']
assert {resource['type'] for resource in app_resources} == {'Microsoft.App/containerApps'}
container = app_resources[0]['properties']['template']['containers'][0]
environment = {entry['name']: entry for entry in container['env']}
assert environment['ROTRACK_LOGGING_ENVIRONMENT']['value'] == 'production'
assert environment['ROTRACK_SERVICE_VERSION']['value'] == '[parameters(\'imageDigest\')]'
for name, secret_ref in {
    'DATABASE_URL': 'database-url',
    'DATABASE_USERNAME': 'database-username',
    'DATABASE_PASSWORD': 'database-password',
    'DATABASE_CA_CERTIFICATE_PEM': 'database-ca-certificate-pem',
    'SUPABASE_JWKS_URI': 'supabase-jwks-uri',
    'SUPABASE_ISSUER_URI': 'supabase-issuer-uri',
}.items():
    assert environment[name].get('secretRef') == secret_ref
    assert 'value' not in environment[name]
assert 'imageDigest' in container['image']
assert app_resources[0]['properties']['configuration']['ingress']['allowInsecure'] is False
assert app_resources[0]['properties']['template']['scale'] == {'minReplicas': 0, 'maxReplicas': 1}
probe_paths = {probe['type']: probe['httpGet']['path'] for probe in container['probes']}
assert probe_paths == {'Liveness': '/api/v1/health', 'Readiness': '/api/v1/readiness'}
PY
printf '%s\n' 'azure production contract: passed (Bicep render only; no Azure mutation)'
