#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

require_command az
require_subscription
require_target
require_mutation_confirmation
validate_foundation_parameters
validate_app_parameters
require_matching_parameter_targets
AZURE_WAIT_FOR_RBAC=1 AZURE_FOUNDATION_PARAMETER_FILE="$FOUNDATION_PARAMETER_FILE" "$SCRIPT_DIR/preflight.sh"

IMAGE_REPOSITORY=$(parameter_value "$APP_PARAMETER_FILE" imageRepository)
IMAGE_DIGEST=$(parameter_value "$APP_PARAMETER_FILE" imageDigest)
export IMAGE_REPOSITORY IMAGE_DIGEST
require_digest

# This readback is deliberately before the mutating deployment. A missing digest
# fails closed instead of allowing ACA to create a broken revision.
METADATA=$(az acr manifest show-metadata \
  --registry "$FOUNDATION_ACR_NAME" \
  --name "$IMAGE_REPOSITORY@$IMAGE_DIGEST" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --only-show-errors \
  --query '{digest:digest,mediaType:mediaType,architecture:architecture,os:os}' \
  --output json)
python3 - "$METADATA" "$IMAGE_DIGEST" <<'PY'
import json
import sys
metadata = json.loads(sys.argv[1])
expected = sys.argv[2]
if metadata.get('digest') != expected:
    raise SystemExit('requested image digest does not exist in the foundation ACR')
if metadata.get('os') != 'linux' or metadata.get('architecture') != 'amd64':
    raise SystemExit('requested image is not linux/amd64')
if metadata.get('mediaType') not in {
    'application/vnd.oci.image.manifest.v1+json',
    'application/vnd.docker.distribution.manifest.v2+json',
}:
    raise SystemExit('requested image media type is not OCI-compatible')
PY

require_retry_timeout
deadline=$(( $(date +%s) + AZURE_APP_DEPLOY_TIMEOUT_SECONDS ))
deployed=0
while :; do
  if az deployment group create \
    --name rotrack-nonproduction-app \
    --resource-group rotrack-nonproduction \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --template-file "$APP_TEMPLATE" \
    --mode Incremental \
    --parameters "@$APP_PARAMETER_FILE" \
    --output none >/dev/null 2>&1; then
    deployed=1
    break
  fi
  now=$(date +%s)
  [ "$now" -lt "$deadline" ] || fail "immutable app deployment did not complete within ${AZURE_APP_DEPLOY_TIMEOUT_SECONDS}s; Azure output was suppressed"
  sleep 5
done
[ "$deployed" = 1 ] || fail 'immutable app deployment did not complete'

printf '%s\n' 'azure app: completed immutable non-production app deployment (secrets redacted)'
AZURE_FOUNDATION_PARAMETER_FILE="$FOUNDATION_PARAMETER_FILE" \
AZURE_APP_PARAMETER_FILE="$APP_PARAMETER_FILE" \
  "$SCRIPT_DIR/readback.sh"
