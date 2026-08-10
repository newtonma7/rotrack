#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
PUBLISH="$ROOT/scripts/azure/publish-image.sh"
TMP=$(mktemp -d /tmp/rotrack-azure-publish-test.XXXXXX)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
BIN="$TMP/bin"
mkdir -p "$BIN"

python3 - "$TMP/foundation.json" <<'PY'
import json
import pathlib
from datetime import datetime, timezone
now = datetime.now(timezone.utc)
start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
end = start.replace(year=start.year + 1)
params = {
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
pathlib.Path(__import__('sys').argv[1]).write_text(json.dumps(params), encoding='utf-8')
PY
chmod 400 "$TMP/foundation.json"

cat > "$BIN/az" <<'SH'
#!/bin/sh
set -eu
ARGS="$*"
if [ "${1:-}" = account ]; then
  printf '%s\n' "$AZURE_SUBSCRIPTION_ID"
elif [ "${1:-}" = group ]; then
  printf '%s\n' eastus
elif [ "${1:-}" = acr ] && [ "${2:-}" = show ]; then
  case "$ARGS" in
    *loginServer*) printf '%s\n' 'rotracknonproductionabc123.azurecr.io' ;;
    *'--query id'*) printf '%s\n' '/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' ;;
    *) printf '%s\n' '{"name":"rotracknonproductionabc123","sku":"Basic","adminUserEnabled":false}' ;;
  esac
elif [ "${1:-}" = resource ]; then
  printf '%s\n' '{"name":"rotrack-nonproduction-logs","sku":"PerGB2018","retentionInDays":30,"dailyQuotaGb":0.1}'
elif [ "${1:-}" = containerapp ] && [ "${2:-}" = env ]; then
  printf '%s\n' rotrack-nonproduction-env
elif [ "${1:-}" = identity ]; then
  printf '%s\n' fake-principal-id
elif [ "${1:-}" = role ] && [ "${2:-}" = assignment ]; then
  printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]'
elif [ "${1:-}" = rest ]; then
  printf '%s\n' "{\"amount\":25,\"timePeriod\":{\"startDate\":\"$FAKE_BUDGET_START\",\"endDate\":\"$FAKE_BUDGET_END\"},\"notifications\":{\"actual50\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":50,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual80\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":80,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]},\"actual100\":{\"enabled\":true,\"operator\":\"GreaterThan\",\"threshold\":100,\"thresholdType\":\"Actual\",\"contactEmails\":[\"owner@example.test\"]}}}"
elif [ "${1:-}" = acr ] && [ "${2:-}" = login ]; then
  case "$ARGS" in
    *expose-token*) printf '%s\n' 'token-secret-marker' ;;
  esac
elif [ "${1:-}" = acr ] && [ "${2:-}" = manifest ]; then
  case "$ARGS" in
    *"--query digest"*) printf '%s\n' "$FAKE_REGISTRY_DIGEST" ;;
    *) printf '%s\n' "{\"digest\":\"$FAKE_REGISTRY_DIGEST\",\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"architecture\":\"amd64\",\"os\":\"linux\"}" ;;
  esac
else
  exit 0
fi
SH
chmod +x "$BIN/az"

cat > "$BIN/docker" <<'SH'
#!/bin/sh
set -eu
[ "${FAKE_ENGINE:-podman}" = docker ] || exit 1
printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
case "${1:-}" in
  info|build) exit 0 ;;
  image)
    case "$*" in
      *"{{.Id}}"*) printf '%s\n' 'sha256:fake-image-id' ;;
      *) printf '%s\n' 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' ;;
    esac
    ;;
esac
case "$*" in
  *' login '*)
    config=
    while [ "$#" -gt 0 ]; do
      if [ "$1" = --config ]; then config=$2; shift 2; continue; fi
      shift
    done
    [ -n "$config" ] && [ -d "$config" ]
    [ "$(stat -c '%a' "$config")" = 700 ]
    cat >/dev/null
    printf '%s\n' docker-auth > "$config/config.json"
    ;;
  *' push '*)
    config=
    while [ "$#" -gt 0 ]; do
      if [ "$1" = --config ]; then config=$2; shift 2; continue; fi
      shift
    done
    [ -n "$config" ] && [ -f "$config/config.json" ]
    ;;
esac
SH
chmod +x "$BIN/docker"

cat > "$BIN/podman" <<'SH'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> "$FAKE_PODMAN_LOG"
case "${1:-}" in
  info|build) exit 0 ;;
  image)
    case "$*" in
      *"{{.Id}}"*) printf '%s\n' 'sha256:fake-image-id' ;;
      *) printf '%s\n' 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' ;;
    esac
    ;;
  login)
    authfile=
    while [ "$#" -gt 0 ]; do
      if [ "$1" = --authfile ]; then authfile=$2; shift 2; continue; fi
      shift
    done
    [ -n "$authfile" ] && [ -f "$authfile" ]
    [ "$(cat "$authfile")" = '{}' ] || { printf '%s\n' 'unexpected end of JSON input' >&2; exit 125; }
    cat >/dev/null
    printf '%s\n' transient-auth > "$authfile"
    ;;
  push)
    authfile=
    digestfile=
    while [ "$#" -gt 0 ]; do
      if [ "$1" = --authfile ]; then authfile=$2; shift 2; continue; fi
      if [ "$1" = --digestfile ]; then digestfile=$2; shift 2; continue; fi
      shift
    done
    [ -n "$authfile" ] && [ -f "$authfile" ]
    [ -n "$digestfile" ]
    printf '%s\n' "$FAKE_LOCAL_DIGEST" > "$digestfile"
    ;;
esac
SH
chmod +x "$BIN/podman"

run_publish() {
  FAKE_ENGINE=${FAKE_ENGINE:-podman} \
  FAKE_PODMAN_LOG="$TMP/podman.log" \
  FAKE_DOCKER_LOG="$TMP/docker.log" \
  FAKE_BUDGET_START="$FAKE_BUDGET_START" \
  FAKE_BUDGET_END="$FAKE_BUDGET_END" \
  FAKE_REGISTRY_DIGEST="$FAKE_REGISTRY_DIGEST" \
  FAKE_LOCAL_DIGEST="$FAKE_LOCAL_DIGEST" \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_NONPRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  ROTRACK_AZURE_CONFIRM=rotrack-nonproduction \
  AZURE_FOUNDATION_PARAMETER_FILE="$TMP/foundation.json" \
  IMAGE_REPOSITORY=rotrack-api IMAGE_TAG=test RELEASE_ID=test REQUIRE_CLEAN=0 \
  PATH="$BIN:$PATH" "$PUBLISH"
}

FAKE_BUDGET_START=$(python3 - <<'PY'
from datetime import datetime, timezone
print(datetime.now(timezone.utc).strftime('%Y-%m-01T00:00:00Z'))
PY
)
FAKE_BUDGET_END=$(python3 - <<'PY'
from datetime import datetime, timezone
now = datetime.now(timezone.utc)
print(now.replace(year=now.year + 1, day=1).strftime('%Y-%m-%dT00:00:00Z'))
PY
)
FAKE_LOCAL_DIGEST=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
FAKE_REGISTRY_DIGEST="$FAKE_LOCAL_DIGEST"
OUTPUT=$(run_publish)
case "$OUTPUT" in *token-secret-marker*) printf '%s\n' 'token leaked to output' >&2; exit 1 ;; esac
PUSH_LINE=$(grep 'push ' "$TMP/podman.log")
case "$PUSH_LINE" in
  *--authfile*--digestfile*) ;;
  *) printf '%s\n' 'Podman push did not receive both transient files' >&2; exit 1 ;;
esac
AUTH_PATH=$(awk '{for (i=1; i<=NF; i++) if ($i == "--authfile") print $(i+1)}' "$TMP/podman.log" | tail -1)
[ ! -e "$AUTH_PATH" ] || { printf '%s\n' 'transient Podman authfile was not removed' >&2; exit 1; }

FAKE_LOCAL_DIGEST=not-a-digest
if run_publish >/dev/null 2>&1; then
  printf '%s\n' 'invalid Podman local digest was accepted' >&2
  exit 1
fi
FAKE_LOCAL_DIGEST=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
FAKE_REGISTRY_DIGEST=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
if run_publish >/dev/null 2>&1; then
  printf '%s\n' 'Podman/ACR digest mismatch was accepted' >&2
  exit 1
fi

FAKE_ENGINE=docker
FAKE_REGISTRY_DIGEST=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
OUTPUT=$(run_publish)
case "$OUTPUT" in *token-secret-marker*) printf '%s\n' 'Docker token leaked to output' >&2; exit 1 ;; esac
DOCKER_LOGIN_LINE=$(grep 'login ' "$TMP/docker.log")
DOCKER_PUSH_LINE=$(grep 'push ' "$TMP/docker.log")
case "$DOCKER_LOGIN_LINE" in *--config*) ;; *) printf '%s\n' 'Docker login did not receive a temporary config' >&2; exit 1 ;; esac
case "$DOCKER_PUSH_LINE" in *--config*) ;; *) printf '%s\n' 'Docker push did not receive the temporary config' >&2; exit 1 ;; esac
DOCKER_CONFIG_PATH=$(awk '{for (i=1; i<=NF; i++) if ($i == "--config") print $(i+1)}' "$TMP/docker.log" | tail -1)
[ ! -e "$DOCKER_CONFIG_PATH" ] || { printf '%s\n' 'temporary Docker config was not removed' >&2; exit 1; }

printf '%s\n' 'azure publish-image fake-engine contract: passed'
