#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

require_command az
require_subscription
require_target
require_mutation_confirmation
validate_foundation_parameters
AZURE_WAIT_FOR_RBAC=1 AZURE_FOUNDATION_PARAMETER_FILE="$FOUNDATION_PARAMETER_FILE" "$SCRIPT_DIR/preflight.sh"
require_repo
require_tag

LOGIN_SERVER=$(az acr show \
  --name "$FOUNDATION_ACR_NAME" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --query loginServer \
  --output tsv)
[ -n "$LOGIN_SERVER" ] || fail 'ACR login server readback was empty'

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker
elif command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
  ENGINE=podman
else
  fail 'a usable Docker or Podman engine is required'
fi

LOCAL_IMAGE_REPOSITORY="$LOGIN_SERVER/$IMAGE_REPOSITORY"
IMAGE_REPOSITORY="$LOCAL_IMAGE_REPOSITORY" \
IMAGE_TAG="$IMAGE_TAG" \
REQUIRE_CLEAN=${REQUIRE_CLEAN:-1} \
  "$ROOT/scripts/container/build-image.sh" >/dev/null
IMAGE_REF="$LOCAL_IMAGE_REPOSITORY:$IMAGE_TAG"
DIGEST_FILE=$(mktemp)
AUTHFILE=
DOCKER_CONFIG_DIR=
cleanup() {
  rm -f "$DIGEST_FILE"
  [ -z "$AUTHFILE" ] || rm -f "$AUTHFILE"
  [ -z "$DOCKER_CONFIG_DIR" ] || rm -rf "$DOCKER_CONFIG_DIR"
}
trap cleanup EXIT HUP INT TERM

if [ "$ENGINE" = docker ]; then
  DOCKER_CONFIG_DIR=$(mktemp -d /tmp/rotrack-azure-docker-config.XXXXXX)
  chmod 700 "$DOCKER_CONFIG_DIR"
  # Keep Docker credentials in a private disposable config rather than the user's normal config.
  az acr login \
    --name "$FOUNDATION_ACR_NAME" \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --expose-token \
    --only-show-errors \
    --query accessToken \
    --output tsv \
    | docker --config "$DOCKER_CONFIG_DIR" login "$LOGIN_SERVER" \
        --username 00000000-0000-0000-0000-000000000000 \
        --password-stdin \
        >/dev/null 2>&1
else
  # Podman does not use Docker's credential helper. The short-lived ACR token is
  # piped directly to stdin and the transient auth file is removed on every exit.
  AUTHFILE=$(mktemp /tmp/rotrack-azure-podman-auth.XXXXXX)
  chmod 600 "$AUTHFILE"
  # Podman expects a valid auth-file document before reading the token stream.
  printf '%s\n' '{}' > "$AUTHFILE"
  az acr login \
    --name "$FOUNDATION_ACR_NAME" \
    --subscription "$AZURE_SUBSCRIPTION_ID" \
    --expose-token \
    --only-show-errors \
    --query accessToken \
    --output tsv \
    | podman login "$LOGIN_SERVER" \
        --username 00000000-0000-0000-0000-000000000000 \
        --password-stdin \
        --authfile "$AUTHFILE" \
        --tls-verify=true \
        >/dev/null 2>&1
fi

if [ "$ENGINE" = podman ]; then
  "$ENGINE" push "$IMAGE_REF" --authfile "$AUTHFILE" --digestfile "$DIGEST_FILE" >/dev/null 2>&1
  LOCAL_DIGEST=$(tr -d '\r\n' < "$DIGEST_FILE")
  require_digest "$LOCAL_DIGEST"
else
  # Docker's push command has no portable digestfile option. Read the immutable
  # digest back from ACR instead of relying on a local engine-specific artifact.
  docker --config "$DOCKER_CONFIG_DIR" push "$IMAGE_REF" >/dev/null 2>&1
  LOCAL_DIGEST=
fi

IMAGE_DIGEST=$(az acr manifest show-metadata \
  --registry "$FOUNDATION_ACR_NAME" \
  --name "$IMAGE_REPOSITORY:$IMAGE_TAG" \
  --subscription "$AZURE_SUBSCRIPTION_ID" \
  --only-show-errors \
  --query digest \
  --output tsv)
require_digest
if [ -n "$LOCAL_DIGEST" ] && [ "$LOCAL_DIGEST" != "$IMAGE_DIGEST" ]; then
  fail 'ACR digest did not match Podman push digestfile'
fi

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
    raise SystemExit('registry digest readback did not match the pushed digest')
if metadata.get('os') != 'linux' or metadata.get('architecture') != 'amd64':
    raise SystemExit('registry image is not linux/amd64')
if metadata.get('mediaType') not in {
    'application/vnd.oci.image.manifest.v1+json',
    'application/vnd.docker.distribution.manifest.v2+json',
}:
    raise SystemExit('registry image media type is not OCI-compatible')
PY

printf 'azure image publish: repository=%s digest=%s media_type=%s os=%s architecture=%s\n' \
  "$LOCAL_IMAGE_REPOSITORY" "$IMAGE_DIGEST" \
  "$(printf '%s' "$METADATA" | python3 -c 'import json,sys; print(json.load(sys.stdin)["mediaType"])')" \
  "$(printf '%s' "$METADATA" | python3 -c 'import json,sys; print(json.load(sys.stdin)["os"])')" \
  "$(printf '%s' "$METADATA" | python3 -c 'import json,sys; print(json.load(sys.stdin)["architecture"])')"
