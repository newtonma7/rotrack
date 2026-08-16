#!/bin/sh
set -eu

AZURE_ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
FOUNDATION_TEMPLATE="$AZURE_ROOT/deploy/azure/foundation.bicep"
APP_TEMPLATE="$AZURE_ROOT/deploy/azure/app.bicep"
AZURE_RESOURCE_GROUP=${AZURE_RESOURCE_GROUP:-rotrack-nonproduction}
AZURE_MANAGED_ENVIRONMENT=${AZURE_MANAGED_ENVIRONMENT:-rotrack-nonproduction-env}
AZURE_CONTAINER_APP=${AZURE_CONTAINER_APP:-rotrack-api-nonproduction}
AZURE_TARGET=${AZURE_TARGET:-nonproduction}

fail() {
  printf 'azure: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

require_subscription() {
  AZURE_SUBSCRIPTION_ID=${AZURE_SUBSCRIPTION_ID:-}
  [ -n "$AZURE_SUBSCRIPTION_ID" ] || fail 'AZURE_SUBSCRIPTION_ID is required'
  printf '%s\n' "$AZURE_SUBSCRIPTION_ID" | grep -Eq '^[0-9a-fA-F-]{36}$' || fail 'AZURE_SUBSCRIPTION_ID must be a GUID'
}

require_target() {
  [ "$AZURE_TARGET" = nonproduction ] || fail 'AZURE_TARGET must be exactly nonproduction'
  [ "$AZURE_RESOURCE_GROUP" = rotrack-nonproduction ] || fail 'resource group must be rotrack-nonproduction'
  [ "$AZURE_MANAGED_ENVIRONMENT" = rotrack-nonproduction-env ] || fail 'managed environment must be rotrack-nonproduction-env'
  [ "$AZURE_CONTAINER_APP" = rotrack-api-nonproduction ] || fail 'container app must be rotrack-api-nonproduction'
  case "${ROTRACK_AZURE_CONFIRM:-}" in
    '' ) ;;
    rotrack-nonproduction ) ;;
    * ) fail 'ROTRACK_AZURE_CONFIRM is not the non-production target' ;;
  esac
}

require_mutation_confirmation() {
  [ "${ROTRACK_AZURE_CONFIRM:-}" = rotrack-nonproduction ] || fail 'set ROTRACK_AZURE_CONFIRM=rotrack-nonproduction to authorize this non-production mutation'
}

require_parameter_file() {
  PARAMETER_FILE=$1
  EXPECTED_MODE=$2
  case "$PARAMETER_FILE" in
    /*) ;;
    *) fail 'parameter files must be absolute paths outside the repository' ;;
  esac
  require_command realpath
  PARAMETER_REALPATH=$(realpath "$PARAMETER_FILE") || fail 'parameter file does not resolve'
  case "$PARAMETER_REALPATH" in
    "$AZURE_ROOT"/*|"$AZURE_ROOT") fail 'parameter file must be outside the repository' ;;
  esac
  [ -f "$PARAMETER_REALPATH" ] || fail 'parameter file must be a regular file'
  require_command stat
  MODE=$(stat -c '%a' "$PARAMETER_REALPATH" 2>/dev/null || stat -f '%Lp' "$PARAMETER_REALPATH")
  [ "$MODE" = "$EXPECTED_MODE" ] || fail "parameter file must have mode $EXPECTED_MODE"
  [ -r "$PARAMETER_REALPATH" ] || fail 'parameter file is not readable'
}

parameter_value() {
  python3 - "$1" "$2" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding='utf-8'))['parameters'][sys.argv[2]]['value'])
PY
}

validate_foundation_parameters() {
  FOUNDATION_PARAMETER_FILE=${AZURE_FOUNDATION_PARAMETER_FILE:-}
  [ -n "$FOUNDATION_PARAMETER_FILE" ] || fail 'AZURE_FOUNDATION_PARAMETER_FILE is required'
  require_command python3
  require_parameter_file "$FOUNDATION_PARAMETER_FILE" 400
  FOUNDATION_PARAMETER_FILE=$PARAMETER_REALPATH
  export FOUNDATION_PARAMETER_FILE
  python3 - "$FOUNDATION_PARAMETER_FILE" <<'PY'
import json
import pathlib
import re
import sys
from datetime import datetime, timezone

path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text(encoding='utf-8'))
params = data.get('parameters', {})
required = {'location', 'acrName', 'budgetAmount', 'budgetStartDate', 'budgetEndDate', 'budgetAlertEmails'}
if set(params) != required:
    raise SystemExit('foundation parameter file has an unexpected parameter set')
values = {name: entry.get('value') for name, entry in params.items()}
if not isinstance(values['location'], str) or not re.fullmatch(r'[A-Za-z0-9.-]+', values['location']):
    raise SystemExit('foundation location is invalid')
if not re.fullmatch(r'rotracknonproduction[a-z0-9]{0,30}', str(values['acrName'])):
    raise SystemExit('foundation acrName must be a rotracknonproduction* name')
if not isinstance(values['budgetAmount'], int) or not 1 <= values['budgetAmount']:
    raise SystemExit('foundation budgetAmount must be positive')
try:
    start = datetime.strptime(values['budgetStartDate'], '%Y-%m-%dT00:00:00Z')
    end = datetime.strptime(values['budgetEndDate'], '%Y-%m-%dT00:00:00Z')
except (TypeError, ValueError):
    raise SystemExit('foundation budget dates must be UTC midnight ISO dates')
now = datetime.now(timezone.utc).replace(tzinfo=None)
if start.day != 1 or end.day != 1 or not 1 <= (end - start).days <= 366 or not (start <= now < end):
    raise SystemExit('foundation budget period must be current, bounded, and month-aligned')
if not isinstance(values['budgetAlertEmails'], list) or not values['budgetAlertEmails']:
    raise SystemExit('foundation budgetAlertEmails must not be empty')
email_pattern = re.compile(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')
if any(not isinstance(email, str) or not email_pattern.fullmatch(email) or email.startswith('<') for email in values['budgetAlertEmails']):
    raise SystemExit('foundation budgetAlertEmails contains an invalid recipient')
for name, value in values.items():
    if isinstance(value, str) and (value.startswith('<') or value.endswith('>')):
        raise SystemExit(f'foundation parameter contains an unfilled placeholder: {name}')
PY
  FOUNDATION_LOCATION=$(parameter_value "$FOUNDATION_PARAMETER_FILE" location)
  FOUNDATION_ACR_NAME=$(parameter_value "$FOUNDATION_PARAMETER_FILE" acrName)
  export FOUNDATION_LOCATION FOUNDATION_ACR_NAME
}

validate_app_parameters() {
  APP_PARAMETER_FILE=${AZURE_APP_PARAMETER_FILE:-}
  [ -n "$APP_PARAMETER_FILE" ] || fail 'AZURE_APP_PARAMETER_FILE is required'
  require_command python3
  require_parameter_file "$APP_PARAMETER_FILE" 600
  APP_PARAMETER_FILE=$PARAMETER_REALPATH
  export APP_PARAMETER_FILE
  python3 - "$APP_PARAMETER_FILE" <<'PY'
import json
import pathlib
import re
import sys
from urllib.parse import urlparse

path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text(encoding='utf-8'))
params = data.get('parameters', {})
required = {
    'location', 'acrName', 'imageRepository', 'imageDigest',
    'databaseUrl', 'databaseUsername', 'databasePassword',
    'databaseCaCertificatePem', 'supabaseJwksUri', 'supabaseIssuerUri',
    'notesHmacSecret', 'corsAllowedOrigins',
}
if set(params) != required:
    raise SystemExit('app parameter file has an unexpected parameter set')
values = {name: entry.get('value') for name, entry in params.items()}
if not isinstance(values['location'], str) or not re.fullmatch(r'[A-Za-z0-9.-]+', values['location']):
    raise SystemExit('app location is invalid')
if not re.fullmatch(r'rotracknonproduction[a-z0-9]{0,30}', str(values['acrName'])):
    raise SystemExit('app acrName must be a rotracknonproduction* name')
if not re.fullmatch(r'[a-z0-9]+([._/-][a-z0-9]+)*', str(values['imageRepository'])):
    raise SystemExit('app imageRepository is invalid')
if not re.fullmatch(r'sha256:[0-9a-f]{64}', str(values['imageDigest'])):
    raise SystemExit('app imageDigest must be an immutable sha256 digest')
database_url = values['databaseUrl']
if not isinstance(database_url, str) or not database_url.startswith('jdbc:postgresql://') or '?' not in database_url:
    raise SystemExit('app databaseUrl is required')
query = database_url.split('?', 1)[1].split('&')
query_values = {}
for item in query:
    key, separator, value = item.partition('=')
    if not separator or key in query_values:
        raise SystemExit('app databaseUrl has duplicate or malformed TLS parameters')
    query_values[key] = value
if query_values.get('sslmode') != 'verify-full' or query_values.get('sslrootcert') != '/tmp/rotrack-certs/supabase-db-ca.crt':
    raise SystemExit('app databaseUrl must use the exact managed TLS contract')
if not isinstance(values['databaseUsername'], str) or not values['databaseUsername'] or not isinstance(values['databasePassword'], str) or not values['databasePassword']:
    raise SystemExit('app database username/password must be nonempty')
notes_hmac_secret = values['notesHmacSecret']
if not isinstance(notes_hmac_secret, str) or len(notes_hmac_secret.encode('utf-8')) < 32:
    raise SystemExit('app notesHmacSecret must contain at least 32 UTF-8 bytes')
if notes_hmac_secret.startswith('replace-with-'):
    raise SystemExit('app notesHmacSecret must not use the repository placeholder pattern')
ca_pem = values['databaseCaCertificatePem']
begin_marker = '-----BEGIN CERTIFICATE-----'
end_marker = '-----END CERTIFICATE-----'
if not isinstance(ca_pem, str) or begin_marker not in ca_pem or end_marker not in ca_pem or ca_pem.index(begin_marker) >= ca_pem.index(end_marker) or not ca_pem[ca_pem.index(begin_marker) + len(begin_marker):ca_pem.index(end_marker)].strip():
    raise SystemExit('app database CA value must contain ordered PEM certificate markers')
issuer = urlparse(str(values['supabaseIssuerUri']))
jwks = urlparse(str(values['supabaseJwksUri']))
if (issuer.scheme, issuer.path, issuer.query, issuer.fragment) != ('https', '/auth/v1', '', '') or not issuer.hostname or issuer.netloc != issuer.hostname or not re.fullmatch(r'[a-z0-9]{20}\.supabase\.co', issuer.hostname):
    raise SystemExit('app Supabase issuer must use a 20-character Supabase project host and exact HTTPS /auth/v1 endpoint')
if (jwks.scheme, jwks.path, jwks.query, jwks.fragment) != ('https', '/auth/v1/.well-known/jwks.json', '', '') or not jwks.hostname or jwks.netloc != jwks.hostname or jwks.hostname != issuer.hostname:
    raise SystemExit('app Supabase JWKS URI must match the issuer host and exact path')
origins = str(values['corsAllowedOrigins']).split(',')
if not origins or any(origin != origin.strip() or not origin.startswith('https://') or origin.endswith('/') or '*' in origin for origin in origins):
    raise SystemExit('app CORS origins must be exact HTTPS origins without wildcards or trailing slashes')
for origin in origins:
    parsed = urlparse(origin)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.netloc != parsed.hostname or parsed.path or parsed.params or parsed.query or parsed.fragment or parsed.hostname in {'localhost', '127.0.0.1', '::1'} or not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\.vercel\.app', parsed.hostname):
        raise SystemExit('app CORS origins must be non-bare HTTPS Vercel hosts without paths or local hosts')
for name, value in values.items():
    if isinstance(value, str) and (value.startswith('<') or value.endswith('>')):
        raise SystemExit(f'app parameter contains an unfilled placeholder: {name}')
PY
  APP_LOCATION=$(parameter_value "$APP_PARAMETER_FILE" location)
  APP_ACR_NAME=$(parameter_value "$APP_PARAMETER_FILE" acrName)
  IMAGE_DIGEST=$(parameter_value "$APP_PARAMETER_FILE" imageDigest)
  export APP_LOCATION APP_ACR_NAME IMAGE_DIGEST
}

require_matching_parameter_targets() {
  [ "$FOUNDATION_LOCATION" = "$APP_LOCATION" ] || fail 'foundation and app regions differ'
  [ "$FOUNDATION_ACR_NAME" = "$APP_ACR_NAME" ] || fail 'foundation and app ACR names differ'
}

require_retry_timeout() {
  AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS=${AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS:-180}
  printf '%s\n' "$AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS" | grep -Eq '^[1-9][0-9]{0,2}$' || fail 'AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS must be 1-999 seconds'
  AZURE_APP_DEPLOY_TIMEOUT_SECONDS=${AZURE_APP_DEPLOY_TIMEOUT_SECONDS:-180}
  printf '%s\n' "$AZURE_APP_DEPLOY_TIMEOUT_SECONDS" | grep -Eq '^[1-9][0-9]{0,2}$' || fail 'AZURE_APP_DEPLOY_TIMEOUT_SECONDS must be 1-999 seconds'
  export AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS AZURE_APP_DEPLOY_TIMEOUT_SECONDS
}

acr_pull_role_count() {
  principal_id=$1
  acr_scope=$2
  role_assignments=$(az role assignment list \
    --assignee-object-id "$principal_id" \
    --all \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --output json 2>/dev/null || printf '[]')
  EXPECTED_ACR_SCOPE="$acr_scope" ROLE_ASSIGNMENTS="$role_assignments" python3 <<'PY'
import json
import os

expected_scope = os.environ['EXPECTED_ACR_SCOPE'].rstrip('/').casefold()
assignments = json.loads(os.environ['ROLE_ASSIGNMENTS'])
role_id = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
count = 0
for assignment in assignments:
    scope = str(assignment.get('scope', '')).rstrip('/').casefold()
    definition = str(assignment.get('roleDefinitionId', '')).rstrip('/').rsplit('/', 1)[-1].casefold()
    if scope == expected_scope and definition == role_id:
        count += 1
print(count)
PY
}

wait_for_acr_pull() {
  require_retry_timeout
  deadline=$(( $(date +%s) + AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS ))
  acr_scope=$(az acr show \
    --name "$FOUNDATION_ACR_NAME" \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --query id \
    --output tsv 2>/dev/null || true)
  while :; do
    principal_id=$(az identity show \
      --name rotrack-api-nonproduction-identity \
      --resource-group rotrack-nonproduction \
      --subscription "$AZURE_SUBSCRIPTION_ID" \
      --query principalId \
      --output tsv 2>/dev/null || true)
    role_count=0
    if [ -n "$principal_id" ] && [ -n "$acr_scope" ]; then
      role_count=$(acr_pull_role_count "$principal_id" "$acr_scope")
    fi
    if printf '%s\n' "$role_count" | grep -Eq '^[1-9][0-9]*$'; then
      return 0
    fi
    now=$(date +%s)
    [ "$now" -lt "$deadline" ] || fail "AcrPull RBAC propagation did not complete within ${AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS}s"
    sleep 5
  done
}

require_digest() {
  digest_to_validate=${1:-${IMAGE_DIGEST:-}}
  printf '%s\n' "$digest_to_validate" | grep -Eq '^sha256:[0-9a-f]{64}$' || fail 'IMAGE_DIGEST must be an immutable sha256 digest'
}

require_repo() {
  IMAGE_REPOSITORY=${IMAGE_REPOSITORY:-rotrack-api}
  printf '%s\n' "$IMAGE_REPOSITORY" | grep -Eq '^[a-z0-9]+([._/-][a-z0-9]+)*$' || fail 'IMAGE_REPOSITORY is invalid'
}

require_tag() {
  IMAGE_TAG=${IMAGE_TAG:-}
  [ -n "$IMAGE_TAG" ] || fail 'IMAGE_TAG is required'
  printf '%s\n' "$IMAGE_TAG" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$' || fail 'IMAGE_TAG is invalid'
}
