#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
AZURE_DIR="$ROOT/deploy/azure"
SCRIPTS_DIR="$ROOT/scripts/azure"

fail() {
  printf 'azure contract: %s\n' "$1" >&2
  exit 1
}

[ -f "$AZURE_DIR/foundation.bicep" ] || fail 'foundation.bicep is missing'
[ -f "$AZURE_DIR/app.bicep" ] || fail 'app.bicep is missing'
[ ! -e "$AZURE_DIR/main.bicep" ] || fail 'all-in-one main.bicep remains'
for script in foundation-provision publish-image app-deploy readback preflight validate; do
  [ -x "$SCRIPTS_DIR/$script.sh" ] || fail "$script.sh is not executable"
done
for test_script in test-publish-image test-rbac-role-scope test-preflight-budget-shape test-readback-scale-shape; do
  [ -x "$AZURE_DIR/tests/$test_script.sh" ] || fail "$test_script.sh is not executable"
done
[ "$(grep -Fc 'az rest' "$SCRIPTS_DIR/preflight.sh")" -eq 1 ] || fail 'budget preflight must use az rest exactly once'
grep -Fq -- 'providers/Microsoft.Consumption/budgets/rotrack-nonproduction-budget?api-version=2023-05-01' "$SCRIPTS_DIR/preflight.sh" || fail 'budget REST URL/API version missing'
grep -Fq -- '--query properties' "$SCRIPTS_DIR/preflight.sh" || fail 'budget REST properties query missing'

# Render/compile is local only. It must not create or update Azure resources.
FOUNDATION_RENDERED=$(mktemp)
APP_RENDERED=$(mktemp)
TMP=$(mktemp -d /tmp/rotrack-azure-contract.XXXXXX)
trap 'rm -f "$FOUNDATION_RENDERED" "$APP_RENDERED"; rm -rf "$TMP"' EXIT HUP INT TERM
az bicep build --file "$AZURE_DIR/foundation.bicep" --outfile "$FOUNDATION_RENDERED" >/dev/null
az bicep build --file "$AZURE_DIR/app.bicep" --outfile "$APP_RENDERED" >/dev/null

python3 - "$FOUNDATION_RENDERED" "$APP_RENDERED" <<'PY'
import json
import pathlib
import sys

foundation = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))
app = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding='utf-8'))
for template in (foundation, app):
    serialized = json.dumps(template)
    for forbidden in ('rotrack-prod', 'rotrack-production', 'arn:aws', 'AWS::'):
        assert forbidden not in serialized, forbidden
assert 'imageDigest' not in json.dumps(foundation)
assert 'DATABASE_PASSWORD' not in json.dumps(foundation)
foundation_resources = foundation['resources']
foundation_types = {resource['type'] for resource in foundation_resources}
required_foundation = {
    'Microsoft.OperationalInsights/workspaces',
    'Microsoft.ContainerRegistry/registries',
    'Microsoft.ManagedIdentity/userAssignedIdentities',
    'Microsoft.Authorization/roleAssignments',
    'Microsoft.App/managedEnvironments',
    'Microsoft.Consumption/budgets',
}
assert required_foundation <= foundation_types
assert 'Microsoft.App/containerApps' not in foundation_types
workspace = next(resource for resource in foundation_resources if resource['type'] == 'Microsoft.OperationalInsights/workspaces')
assert workspace['properties']['sku']['name'] == 'PerGB2018'
assert workspace['properties']['retentionInDays'] == 30
assert workspace['properties']['workspaceCapping']['dailyQuotaGb'] in (0.1, "[json('0.1')]")
registry = next(resource for resource in foundation_resources if resource['type'] == 'Microsoft.ContainerRegistry/registries')
assert registry['sku']['name'] == 'Basic'
budget = next(resource for resource in foundation_resources if resource['type'] == 'Microsoft.Consumption/budgets')
budget_props = budget['properties']
assert 'startDate' in budget_props['timePeriod'] and 'endDate' in budget_props['timePeriod']
notifications = budget_props['notifications']
assert {name: notifications[name]['threshold'] for name in ('actual50', 'actual80', 'actual100')} == {'actual50': 50, 'actual80': 80, 'actual100': 100}
assert all(notifications[name]['enabled'] and notifications[name]['operator'] == 'GreaterThan' and notifications[name]['thresholdType'] == 'Actual' for name in ('actual50', 'actual80', 'actual100'))
app_resources = app['resources']
assert {resource['type'] for resource in app_resources} == {'Microsoft.App/containerApps'}
container_app = app_resources[0]
props = container_app['properties']
assert props['configuration']['activeRevisionsMode'] == 'Multiple'
assert props['template']['terminationGracePeriodSeconds'] == 30
assert props['template']['scale'] == {'minReplicas': 1, 'maxReplicas': 1}
ingress = props['configuration']['ingress']
assert ingress['external'] is True and ingress['targetPort'] == 8080 and ingress['allowInsecure'] is False
assert ingress['traffic'] == [{'latestRevision': True, 'weight': 100}]
container = props['template']['containers'][0]
assert 'imageDigest' in container['image']
env = {entry['name']: entry for entry in container['env']}
expected = {
    'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD',
    'DATABASE_CA_CERTIFICATE_PEM', 'SUPABASE_JWKS_URI', 'SUPABASE_ISSUER_URI',
    'PORT', 'DATABASE_CA_CERTIFICATE_PATH', 'DATABASE_CONNECTION_TIMEOUT_MS',
    'DATABASE_POOL_VALIDATION_TIMEOUT_MS', 'DATABASE_MAXIMUM_POOL_SIZE',
    'DATABASE_MINIMUM_IDLE', 'READINESS_CACHE_TTL', 'SUPABASE_JWT_AUDIENCE',
    'CORS_ALLOWED_ORIGINS', 'ROTRACK_MUTATION_RATE_LIMIT_REQUESTS',
    'ROTRACK_MUTATION_RATE_LIMIT_WINDOW', 'ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS',
    'SERVER_SHUTDOWN', 'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE',
    'LOGGING_STRUCTURED_FORMAT_CONSOLE', 'ROTRACK_STRUCTURED_LOGGING_ENABLED',
    'ROTRACK_LOGGING_ENVIRONMENT', 'ROTRACK_SERVICE_VERSION',
    'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY', 'LOGGING_LEVEL_ORG_HIBERNATE_SQL',
    'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND', 'SPRING_JPA_SHOW_SQL',
}
assert set(env) == expected, sorted(set(env) ^ expected)
assert env['ROTRACK_MUTATION_RATE_LIMIT_REQUESTS']['value'] == '30'
assert env['ROTRACK_MUTATION_RATE_LIMIT_WINDOW']['value'] == '1m'
assert env['ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS']['value'] == '10000'
assert env['ROTRACK_LOGGING_ENVIRONMENT']['value'] == 'production'
assert 'imageDigest' in json.dumps(env['ROTRACK_SERVICE_VERSION'])
probes = {probe['type']: probe for probe in container['probes']}
assert probes['Liveness']['httpGet']['path'] == '/api/v1/health'
assert probes['Readiness']['httpGet']['path'] == '/api/v1/readiness'
PY

# Strict external parameter files: foundation is non-secret mode 0400; app is secret mode 0600.
python3 - "$TMP/foundation.json" "$TMP/app.json" <<'PY'
import json
import pathlib
import sys
from datetime import datetime, timezone
now = datetime.now(timezone.utc)
start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
end = start.replace(year=start.year + 1)
foundation = {
    '$schema': 'https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#',
    'contentVersion': '1.0.0.0',
    'parameters': {
        'location': {'value': 'eastus'},
        'acrName': {'value': 'rotracknonproductionabc123'},
        'budgetAmount': {'value': 25},
        'budgetStartDate': {'value': start.strftime('%Y-%m-%dT00:00:00Z')},
        'budgetEndDate': {'value': end.strftime('%Y-%m-%dT00:00:00Z')},
        'budgetAlertEmails': {'value': ['owner@example.test']},
    },
}
app = {
    '$schema': foundation['$schema'],
    'contentVersion': '1.0.0.0',
    'parameters': {
        'location': {'value': 'eastus'},
        'acrName': {'value': 'rotracknonproductionabc123'},
        'imageRepository': {'value': 'rotrack-api'},
        'imageDigest': {'value': 'sha256:' + 'a' * 64},
        'databaseUrl': {'value': 'jdbc:postgresql://db.invalid/postgres?sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt'},
        'databaseUsername': {'value': 'rotrack_runtime'},
        'databasePassword': {'value': 'test-only-password'},
        'databaseCaCertificatePem': {'value': '-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----'},
        'supabaseJwksUri': {'value': 'https://abcdefghijklmnopqrst.supabase.co/auth/v1/.well-known/jwks.json'},
        'supabaseIssuerUri': {'value': 'https://abcdefghijklmnopqrst.supabase.co/auth/v1'},
        'corsAllowedOrigins': {'value': 'https://preview.vercel.app'},
    },
}
pathlib.Path(sys.argv[1]).write_text(json.dumps(foundation), encoding='utf-8')
pathlib.Path(sys.argv[2]).write_text(json.dumps(app), encoding='utf-8')
PY
chmod 400 "$TMP/foundation.json"
chmod 600 "$TMP/app.json"
sh -c '. "$1"; AZURE_FOUNDATION_PARAMETER_FILE="$2"; validate_foundation_parameters; AZURE_APP_PARAMETER_FILE="$3"; validate_app_parameters; require_matching_parameter_targets' \
  "$ROOT/scripts/azure/parameter-validation.sh" "$SCRIPTS_DIR/_common.sh" "$TMP/foundation.json" "$TMP/app.json"
cp "$TMP/foundation.json" "$TMP/foundation-valid.json"
cp "$TMP/app.json" "$TMP/app-valid.json"
expect_foundation_rejected() {
  name=$1
  chmod 600 "$TMP/foundation.json"
  python3 - "$TMP/foundation-valid.json" "$TMP/foundation.json" "$name" <<'PY'
import json
import sys
source, destination, name = sys.argv[1:]
data = json.load(open(source))
if name == 'expired-period':
    data['parameters']['budgetStartDate']['value'] = '2025-01-01T00:00:00Z'
    data['parameters']['budgetEndDate']['value'] = '2025-12-01T00:00:00Z'
elif name == 'bad-email':
    data['parameters']['budgetAlertEmails']['value'] = ['not-an-email']
else:
    raise SystemExit(name)
json.dump(data, open(destination, 'w'))
PY
  chmod 400 "$TMP/foundation.json"
  if sh -c '. "$1"; AZURE_FOUNDATION_PARAMETER_FILE="$2"; validate_foundation_parameters' \
    "$ROOT/scripts/azure/parameter-validation.sh" "$SCRIPTS_DIR/_common.sh" "$TMP/foundation.json" >/dev/null 2>&1; then
    fail "foundation invalid case accepted: $name"
  fi
}
expect_foundation_rejected expired-period
expect_foundation_rejected bad-email
expect_app_rejected() {
  name=$1
  python3 - "$TMP/app-valid.json" "$TMP/app.json" "$name" <<'PY'
import json
import sys
source, destination, name = sys.argv[1:]
data = json.load(open(source))
values = data['parameters']
if name == 'bad-jdbc':
    values['databaseUrl']['value'] = values['databaseUrl']['value'].replace('sslmode=verify-full', 'sslmode=require')
elif name == 'empty-username':
    values['databaseUsername']['value'] = ''
elif name == 'empty-password':
    values['databasePassword']['value'] = ''
elif name == 'bad-pem':
    values['databaseCaCertificatePem']['value'] = 'not-a-pem'
elif name == 'bad-issuer':
    values['supabaseIssuerUri']['value'] = 'https://abcdefghijklmnopqrst.supabase.co/auth/v1/wrong'
elif name == 'bad-jwks-host':
    values['supabaseJwksUri']['value'] = 'https://other.example.test/auth/v1/.well-known/jwks.json'
elif name == 'bad-cors':
    values['corsAllowedOrigins']['value'] = 'http://preview.vercel.app'
elif name == 'bad-cors-host':
    values['corsAllowedOrigins']['value'] = 'https://preview.example.test'
elif name == 'bare-vercel':
    values['corsAllowedOrigins']['value'] = 'https://vercel.app'
else:
    raise SystemExit(name)
json.dump(data, open(destination, 'w'))
PY
  chmod 600 "$TMP/app.json"
  if sh -c '. "$1"; AZURE_APP_PARAMETER_FILE="$2"; validate_app_parameters' \
    "$ROOT/scripts/azure/parameter-validation.sh" "$SCRIPTS_DIR/_common.sh" "$TMP/app.json" >/dev/null 2>&1; then
    fail "app invalid case accepted: $name"
  fi
}
for invalid_case in bad-jdbc empty-username empty-password bad-pem bad-issuer bad-jwks-host bad-cors bad-cors-host bare-vercel; do
  expect_app_rejected "$invalid_case"
done
chmod 600 "$TMP/foundation.json"
cp "$TMP/foundation-valid.json" "$TMP/foundation.json"
cp "$TMP/app-valid.json" "$TMP/app.json"
chmod 600 "$TMP/app.json"
if sh -c '. "$1"; AZURE_FOUNDATION_PARAMETER_FILE="$2"; validate_foundation_parameters' \
  "$ROOT/scripts/azure/parameter-validation.sh" "$SCRIPTS_DIR/_common.sh" "$TMP/foundation.json" >/dev/null 2>&1; then
  fail 'foundation parameter mode 0600 was accepted'
fi
chmod 400 "$TMP/foundation.json"
python3 - "$TMP/app.json" <<'PY'
import json
import sys
path = sys.argv[1]
data = json.load(open(path))
data['parameters']['unexpected'] = {'value': 'rejected'}
json.dump(data, open(path, 'w'))
PY
chmod 600 "$TMP/app.json"
if sh -c '. "$1"; AZURE_APP_PARAMETER_FILE="$2"; validate_app_parameters' \
  "$ROOT/scripts/azure/parameter-validation.sh" "$SCRIPTS_DIR/_common.sh" "$TMP/app.json" >/dev/null 2>&1; then
  fail 'app parameter extra key was accepted'
fi

# Bootstrap ordering is explicit: foundation -> publish/readback -> app, and the
# app digest existence readback precedes its mutating deployment.
python3 - "$SCRIPTS_DIR/foundation-provision.sh" "$SCRIPTS_DIR/publish-image.sh" "$SCRIPTS_DIR/app-deploy.sh" "$SCRIPTS_DIR/_common.sh" <<'PY'
import pathlib
import sys
foundation, publish, app, common = [pathlib.Path(item).read_text(encoding='utf-8') for item in sys.argv[1:]]
assert 'FOUNDATION_TEMPLATE' in foundation and 'APP_TEMPLATE' not in foundation
assert 'imageDigest' not in foundation
assert 'databasePassword' not in foundation
assert publish.index('preflight.sh') < publish.index('build-image.sh') < publish.index('podman login')
assert 'if [ "$ENGINE" = podman ]; then' in publish
assert '"$ENGINE" push "$IMAGE_REF" --authfile "$AUTHFILE" --digestfile "$DIGEST_FILE"' in publish
assert 'docker --config "$DOCKER_CONFIG_DIR" push "$IMAGE_REF" >/dev/null 2>&1' in publish
assert 'docker push' not in publish
assert app.index('preflight.sh') < app.index('az acr manifest show-metadata') < app.index('az deployment group create')
assert 'AcrPull RBAC propagation did not complete within' in common
assert 'AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS' in common
assert 'immutable app deployment did not complete within' in app
assert '--mode Incremental' in foundation and '--mode Incremental' in app
PY

# Podman auth is stdin-only, transient, and has a Docker-specific path as well.
grep -Fq -- '--expose-token' "$SCRIPTS_DIR/publish-image.sh" || fail 'Podman expose-token path missing'
grep -Fq -- '--password-stdin' "$SCRIPTS_DIR/publish-image.sh" || fail 'Podman password-stdin path missing'
grep -Fq -- '--authfile "$AUTHFILE"' "$SCRIPTS_DIR/publish-image.sh" || fail 'Podman transient authfile missing'
grep -Fq -- 'rm -f "$AUTHFILE"' "$SCRIPTS_DIR/publish-image.sh" || fail 'Podman authfile cleanup missing'
grep -Fq -- 'DOCKER_CONFIG_DIR=$(mktemp -d /tmp/rotrack-azure-docker-config.XXXXXX)' "$SCRIPTS_DIR/publish-image.sh" || fail 'Docker ephemeral config missing'
grep -Fq -- 'docker --config "$DOCKER_CONFIG_DIR" login' "$SCRIPTS_DIR/publish-image.sh" || fail 'Docker ephemeral login path missing'
grep -Fq -- 'docker --config "$DOCKER_CONFIG_DIR" push' "$SCRIPTS_DIR/publish-image.sh" || fail 'Docker ephemeral push path missing'
if grep -Eq 'id:id|resourceId|properties\.secrets' "$SCRIPTS_DIR/readback.sh"; then
  fail 'readback contains a resource identifier or secret collection'
fi
grep -Fq -- 'secret values and subscription/resource IDs omitted' "$SCRIPTS_DIR/readback.sh" || fail 'readback redaction contract missing'
grep -Fq -- "'imageDigest': expected_digest" "$SCRIPTS_DIR/readback.sh" || fail 'readback image digest evidence label missing'
grep -Fq -- "'DATABASE_URL': 'database-url'" "$SCRIPTS_DIR/readback.sh" || fail 'readback DATABASE_URL secretRef mapping missing'
grep -Fq -- "'SUPABASE_ISSUER_URI': 'supabase-issuer-uri'" "$SCRIPTS_DIR/readback.sh" || fail 'readback issuer secretRef mapping missing'

# A rejected production target must fail before any Azure CLI invocation.
FAKE_AZ="$TMP/fake-bin"
mkdir -p "$FAKE_AZ"
cat > "$FAKE_AZ/az" <<'SH'
#!/bin/sh
printf 'az must not be invoked for a rejected target\n' >&2
exit 99
SH
chmod +x "$FAKE_AZ/az"
if PATH="$FAKE_AZ:$PATH" \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  ROTRACK_AZURE_CONFIRM=rotrack-production \
  "$SCRIPTS_DIR/foundation-provision.sh" >/dev/null 2>&1; then
  fail 'foundation provision accepted a production confirmation'
fi
if PATH="$FAKE_AZ:$PATH" \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_TARGET=production \
  ROTRACK_AZURE_CONFIRM=rotrack-nonproduction \
  "$SCRIPTS_DIR/foundation-provision.sh" >/dev/null 2>&1; then
  fail 'foundation provision accepted a production target selector'
fi
for mutator in publish-image app-deploy; do
  if PATH="$FAKE_AZ:$PATH" \
    AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    ROTRACK_AZURE_CONFIRM=rotrack-production \
    "$SCRIPTS_DIR/$mutator.sh" >/dev/null 2>&1; then
    fail "$mutator accepted a production confirmation"
  fi
done

printf '%s\n' 'azure contract: passed'
