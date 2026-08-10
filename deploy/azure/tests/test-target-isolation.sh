#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
NONPROD="$ROOT/scripts/azure"
PROD="$ROOT/scripts/azure/production"
PROD_TEMPLATES="$ROOT/deploy/azure/production"
TMP=$(mktemp -d /tmp/rotrack-azure-target-isolation.XXXXXX)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
BIN="$TMP/bin"
mkdir -p "$BIN"
cat > "$BIN/az" <<'SH'
#!/bin/sh
printf 'az must not be invoked for a rejected target\n' >&2
exit 99
SH
chmod +x "$BIN/az"

fail() {
  printf 'target isolation: %s\n' "$1" >&2
  exit 1
}

[ -x "$PROD/foundation-provision.sh" ] || fail 'production foundation lane is missing'
[ -x "$PROD/publish-image.sh" ] || fail 'production image lane is missing'
[ -x "$PROD/app-deploy.sh" ] || fail 'production app lane is missing'
[ -x "$PROD/preflight.sh" ] || fail 'production preflight lane is missing'
[ -x "$PROD/readback.sh" ] || fail 'production readback lane is missing'
[ -f "$PROD_TEMPLATES/foundation.bicep" ] || fail 'production foundation template is missing'
[ -f "$PROD_TEMPLATES/app.bicep" ] || fail 'production app template is missing'

# The existing non-production lane remains hard-coded and must reject every
# production selector/name before it reaches Azure CLI.
for script in foundation-provision publish-image app-deploy preflight readback; do
  if PATH="$BIN:$PATH" \
    AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    AZURE_NONPRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    AZURE_TARGET=production \
    AZURE_RESOURCE_GROUP=rotrack-production \
    AZURE_MANAGED_ENVIRONMENT=rotrack-production-env \
    AZURE_CONTAINER_APP=rotrack-api-production \
    ROTRACK_AZURE_CONFIRM=rotrack-production \
    "$NONPROD/$script.sh" >/dev/null 2>&1; then
    fail "non-production $script accepted production target"
  fi
done

# A matching resource name is not enough: each lane must select its approved
# subscription, and the production confirmation is intentionally separate.
if PATH="$BIN:$PATH" \
  AZURE_SUBSCRIPTION_ID=11111111-1111-1111-1111-111111111111 \
  AZURE_NONPRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_TARGET=nonproduction \
  ROTRACK_AZURE_CONFIRM=rotrack-nonproduction \
  "$NONPROD/foundation-provision.sh" >/dev/null 2>&1; then
  fail 'non-production lane accepted the production subscription'
fi

# The production lane has its own confirmation and must reject non-production
# selectors/names before it reaches Azure CLI.
for script in foundation-provision publish-image app-deploy preflight readback; do
  if PATH="$BIN:$PATH" \
    AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    AZURE_PRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
    AZURE_NONPRODUCTION_SUBSCRIPTION_ID=11111111-1111-1111-1111-111111111111 \
    AZURE_TARGET=nonproduction \
    AZURE_RESOURCE_GROUP=rotrack-nonproduction \
    AZURE_MANAGED_ENVIRONMENT=rotrack-nonproduction-env \
    AZURE_CONTAINER_APP=rotrack-api-nonproduction \
    ROTRACK_AZURE_CONFIRM=rotrack-nonproduction \
    "$PROD/$script.sh" >/dev/null 2>&1; then
    fail "production $script accepted non-production target"
  fi
done
if PATH="$BIN:$PATH" \
  AZURE_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_PRODUCTION_SUBSCRIPTION_ID=00000000-0000-0000-0000-000000000000 \
  AZURE_NONPRODUCTION_SUBSCRIPTION_ID=11111111-1111-1111-1111-111111111111 \
  AZURE_TARGET=production \
  ROTRACK_AZURE_CONFIRM=rotrack-production \
  "$PROD/foundation-provision.sh" >/dev/null 2>&1; then
  fail 'production lane accepted the non-production confirmation variable'
fi

# Production templates must contain only the production Azure boundary, while
# non-production templates must not gain a production path.
for template in "$PROD_TEMPLATES/foundation.bicep" "$PROD_TEMPLATES/app.bicep"; do
  grep -Fq 'rotrack-production' "$template" || fail "production template lacks production target: $template"
  if grep -Fq 'rotrack-nonproduction' "$template"; then
    fail "production template contains non-production target: $template"
  fi
done
for template in "$ROOT/deploy/azure/foundation.bicep" "$ROOT/deploy/azure/app.bicep"; do
  if grep -Fq 'rotrack-production' "$template"; then
    fail "non-production template contains production target: $template"
  fi
done

printf '%s\n' 'azure target isolation contract: passed'
