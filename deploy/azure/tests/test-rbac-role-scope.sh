#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
TMP=$(mktemp -d /tmp/rotrack-azure-rbac-test.XXXXXX)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
BIN="$TMP/bin"
mkdir -p "$BIN"
cat > "$BIN/az" <<'SH'
#!/bin/sh
set -eu
case "$*" in
  *'acr show'*'--query id'*) printf '%s\n' '/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' ;;
  *'identity show'*) printf '%s\n' fake-principal-id ;;
  *'role assignment list'*'--role AcrPull'*)
    printf '%s\n' 'ValueError: No value for given attribute' >&2
    exit 1
    ;;
  *'role assignment list'*)
    case "${FAKE_RBAC_CASE:-success}" in
      success)
        printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]'
        ;;
      wrong-role)
        printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/00000000-0000-0000-0000-000000000000","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123"}]'
        ;;
      wrong-registry)
        printf '%s\n' '[{"roleDefinitionId":"/subscriptions/sub/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d","scope":"/subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/otheracr"}]'
        ;;
    esac
    ;;
esac
SH
chmod +x "$BIN/az"

AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
FOUNDATION_ACR_NAME=rotracknonproductionabc123 \
AZURE_RBAC_PROPAGATION_TIMEOUT_SECONDS=1 \
PATH="$BIN:$PATH" \
  sh -c '. "$1"; wait_for_acr_pull' "$ROOT/scripts/azure/rbac-test.sh" "$ROOT/scripts/azure/_common.sh"

for negative_case in wrong-role wrong-registry; do
  count=$(FAKE_RBAC_CASE="$negative_case" \
    AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    FOUNDATION_ACR_NAME=rotracknonproductionabc123 \
    PATH="$BIN:$PATH" \
      sh -c '. "$1"; acr_pull_role_count fake-principal-id /subscriptions/sub/resourceGroups/rotrack-nonproduction/providers/Microsoft.ContainerRegistry/registries/rotracknonproductionabc123' "$ROOT/scripts/azure/rbac-test.sh" "$ROOT/scripts/azure/_common.sh")
  [ "$count" = 0 ] || { printf 'accepted RBAC negative case: %s\n' "$negative_case" >&2; exit 1; }
done
if grep -Fq -- '--role AcrPull' "$ROOT/scripts/azure/_common.sh" "$ROOT/scripts/azure/preflight.sh"; then
  printf '%s\n' 'role-filter flag must not be used with Azure CLI 2.89' >&2
  exit 1
fi

printf '%s\n' 'azure RBAC role/scope contract: passed'
