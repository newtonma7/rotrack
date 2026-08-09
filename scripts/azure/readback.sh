#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

require_command az
require_subscription
require_target
validate_foundation_parameters
validate_app_parameters
require_matching_parameter_targets
AZURE_FOUNDATION_PARAMETER_FILE="$FOUNDATION_PARAMETER_FILE" "$SCRIPT_DIR/preflight.sh"
IMAGE_REPOSITORY=$(parameter_value "$APP_PARAMETER_FILE" imageRepository)
IMAGE_DIGEST=$(parameter_value "$APP_PARAMETER_FILE" imageDigest)
export IMAGE_REPOSITORY IMAGE_DIGEST
require_digest

ACR_LOGIN_SERVER=$(az acr show \
  --name "$FOUNDATION_ACR_NAME" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query loginServer \
  --output tsv)
APP_READBACK=$(az containerapp show \
  --name rotrack-api-nonproduction \
  --resource-group rotrack-nonproduction \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query '{name:name,latestRevisionName:properties.latestRevisionName,fqdn:properties.configuration.ingress.fqdn,image:properties.template.containers[0].image,scale:properties.template.scale,terminationGracePeriodSeconds:properties.template.terminationGracePeriodSeconds,ingress:properties.configuration.ingress,env:properties.template.containers[0].env,probes:properties.template.containers[0].probes}' \
  --output json)
export APP_READBACK ACR_LOGIN_SERVER
python3 - <<'PY'
import json
import os

app = json.loads(os.environ['APP_READBACK'])
expected_digest = os.environ['IMAGE_DIGEST']
expected_repo = os.environ['IMAGE_REPOSITORY']
expected_server = os.environ['ACR_LOGIN_SERVER']
expected_image = f'{expected_server}/{expected_repo}@{expected_digest}'
env = app.pop('env', [])
probes = app.pop('probes', [])
if app.get('image') != expected_image:
    raise SystemExit('running app image is not exactly the requested registry digest')
environment = {entry['name']: entry for entry in env}
required_names = {
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
if set(environment) != required_names:
    raise SystemExit('running app environment names do not match the contract')
secret_mapping = {
    'DATABASE_URL': 'database-url',
    'DATABASE_USERNAME': 'database-username',
    'DATABASE_PASSWORD': 'database-password',
    'DATABASE_CA_CERTIFICATE_PEM': 'database-ca-certificate-pem',
    'SUPABASE_JWKS_URI': 'supabase-jwks-uri',
    'SUPABASE_ISSUER_URI': 'supabase-issuer-uri',
}
for name, expected_secret in secret_mapping.items():
    entry = environment[name]
    if entry.get('secretRef') != expected_secret or 'value' in entry:
        raise SystemExit(f'secretRef mapping mismatch: {name}')
static_values = {
    'PORT': '8080',
    'DATABASE_CA_CERTIFICATE_PATH': '/tmp/rotrack-certs/supabase-db-ca.crt',
    'DATABASE_CONNECTION_TIMEOUT_MS': '5000',
    'DATABASE_POOL_VALIDATION_TIMEOUT_MS': '2000',
    'DATABASE_MAXIMUM_POOL_SIZE': '5',
    'DATABASE_MINIMUM_IDLE': '0',
    'READINESS_CACHE_TTL': '5s',
    'SUPABASE_JWT_AUDIENCE': 'authenticated',
    'ROTRACK_MUTATION_RATE_LIMIT_REQUESTS': '30',
    'ROTRACK_MUTATION_RATE_LIMIT_WINDOW': '1m',
    'ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS': '10000',
    'SERVER_SHUTDOWN': 'graceful',
    'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE': '25s',
    'LOGGING_STRUCTURED_FORMAT_CONSOLE': 'ecs',
    'ROTRACK_STRUCTURED_LOGGING_ENABLED': 'true',
    'ROTRACK_LOGGING_ENVIRONMENT': 'staging',
    'ROTRACK_SERVICE_VERSION': expected_digest,
    'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY': 'WARN',
    'LOGGING_LEVEL_ORG_HIBERNATE_SQL': 'OFF',
    'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND': 'OFF',
    'SPRING_JPA_SHOW_SQL': 'false',
}
for name, value in static_values.items():
    if environment.get(name, {}).get('value') != value:
        raise SystemExit(f'running app value mismatch: {name}')
if app['ingress']['external'] is not True or app['ingress']['targetPort'] != 8080 or app['ingress']['allowInsecure'] is not False:
    raise SystemExit('running ingress is not HTTPS-only on port 8080')
scale = app.get('scale', {})
if scale.get('minReplicas') != 0 or scale.get('maxReplicas') != 1:
    raise SystemExit('running scale bounds do not match 0/1')
if scale.get('rules'):
    raise SystemExit('running app has unexpected nonempty scaling rules')
if app.get('terminationGracePeriodSeconds') != 30:
    raise SystemExit('running termination grace period is not 30 seconds')
probe_paths = {probe['type']: probe['httpGet']['path'] for probe in probes}
if probe_paths != {'Liveness': '/api/v1/health', 'Readiness': '/api/v1/readiness'}:
    raise SystemExit('running probe paths do not match liveness/readiness contract')
fqdn = app.pop('fqdn', None)
image = app.pop('image', None)
app['environmentNames'] = sorted(environment)
app['probePaths'] = probe_paths
app['nonSecretReleaseEvidence'] = {
    'fqdn': fqdn,
    'imageDigest': expected_digest,
    'imageReference': image,
    'serviceVersion': expected_digest,
}
print(json.dumps(app, separators=(',', ':')))
PY
printf '%s\n' 'azure readback: exact digest/service version passed; secret values and subscription/resource IDs omitted; FQDN/image are non-secret release evidence'
