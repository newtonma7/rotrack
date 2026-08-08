#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"

for script in \
  "${SCRIPT_DIR}/_common.sh" \
  "${SCRIPT_DIR}/staging-smoke.sh" \
  "${SCRIPT_DIR}/rollback-rehearsal.sh"; do
  bash -n "$script"
done

# Release scripts must not construct authenticated curl requests, print storage
# state, enable xtrace, or evaluate operator-provided command strings.
if grep -En -- 'curl .*(-H|--header)|Authorization:|Cookie:|cat .*STORAGE|set -x|eval ' \
  "${SCRIPT_DIR}/_common.sh" \
  "${SCRIPT_DIR}/staging-smoke.sh" \
  "${SCRIPT_DIR}/rollback-rehearsal.sh"; then
  printf 'ERROR: unsafe release-script pattern found\n' >&2
  exit 1
fi

expect_failure() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    printf 'ERROR: negative check unexpectedly passed: %s\n' "$description" >&2
    exit 1
  fi
  printf 'PASS: %s failed closed\n' "$description"
}

expect_failure "smoke without explicit staging configuration" \
  env -i PATH="$PATH" bash "${SCRIPT_DIR}/staging-smoke.sh"

expect_failure "placeholder staging configuration" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-placeholder \
    ROTRACK_STAGING_FRONTEND_URL=https://frontend.staging.example.invalid \
    ROTRACK_STAGING_API_URL=https://api.staging.example.invalid/api/v1 \
    bash "${SCRIPT_DIR}/staging-smoke.sh"

expect_failure "smoke with incomplete protected inventory configuration" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-reviewed \
    ROTRACK_STAGING_FRONTEND_URL=https://app.example.test \
    ROTRACK_STAGING_API_URL=https://api.example.test/api/v1 \
    bash "${SCRIPT_DIR}/staging-smoke.sh"

TEMPORARY_DIRECTORY="$(mktemp -d)"
trap 'rm -rf -- "$TEMPORARY_DIRECTORY"' EXIT
cat >"${TEMPORARY_DIRECTORY}/approved-staging.inventory" <<'INVENTORY'
ROTRACK_STAGING_TARGET_ID=staging-reviewed
ROTRACK_STAGING_FRONTEND_URL=https://frontend.staging.example.test
ROTRACK_STAGING_API_URL=https://api.staging.example.test/api/v1
ROTRACK_STAGING_SUPABASE_PROJECT_REF=stagingaaaaaaaaaaaaa
ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF=developmentaaaaaaaaa
ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF=productionaaaaaaaaaa
ROTRACK_PRODUCTION_FRONTEND_URL=https://frontend.production.example.test
ROTRACK_PRODUCTION_API_URL=https://api.production.example.test/api/v1
INVENTORY
chmod 0600 "${TEMPORARY_DIRECTORY}/approved-staging.inventory"
expect_failure "target URLs differing from the approved staging inventory" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-reviewed \
    ROTRACK_STAGING_FRONTEND_URL=https://app.production.example.test \
    ROTRACK_STAGING_API_URL=https://api.production.example.test/api/v1 \
    ROTRACK_STAGING_SUPABASE_PROJECT_REF=stagingaaaaaaaaaaaaa \
    ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF=developmentaaaaaaaaa \
    ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF=productionaaaaaaaaaa \
    ROTRACK_PRODUCTION_FRONTEND_URL=https://frontend.production.example.test \
    ROTRACK_PRODUCTION_API_URL=https://api.production.example.test/api/v1 \
    ROTRACK_STAGING_INVENTORY_FILE="${TEMPORARY_DIRECTORY}/approved-staging.inventory" \
    bash -c 'source "$1"; release_validate_staging_context' _ "${SCRIPT_DIR}/_common.sh"

cat >"${TEMPORARY_DIRECTORY}/production-valued.inventory" <<'INVENTORY'
ROTRACK_STAGING_TARGET_ID=staging-reviewed
ROTRACK_STAGING_FRONTEND_URL=https://frontend.production.example.test
ROTRACK_STAGING_API_URL=https://api.production.example.test/api/v1
ROTRACK_STAGING_SUPABASE_PROJECT_REF=productionaaaaaaaaaa
ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF=developmentaaaaaaaaa
ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF=productionaaaaaaaaaa
ROTRACK_PRODUCTION_FRONTEND_URL=https://frontend.production.example.test
ROTRACK_PRODUCTION_API_URL=https://api.production.example.test/api/v1
INVENTORY
chmod 0600 "${TEMPORARY_DIRECTORY}/production-valued.inventory"
expect_failure "internally consistent production-valued staging inventory" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-reviewed \
    ROTRACK_STAGING_FRONTEND_URL=https://frontend.production.example.test \
    ROTRACK_STAGING_API_URL=https://api.production.example.test/api/v1 \
    ROTRACK_STAGING_SUPABASE_PROJECT_REF=productionaaaaaaaaaa \
    ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF=developmentaaaaaaaaa \
    ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF=productionaaaaaaaaaa \
    ROTRACK_PRODUCTION_FRONTEND_URL=https://frontend.production.example.test \
    ROTRACK_PRODUCTION_API_URL=https://api.production.example.test/api/v1 \
    ROTRACK_STAGING_INVENTORY_FILE="${TEMPORARY_DIRECTORY}/production-valued.inventory" \
    bash -c 'source "$1"; release_validate_staging_context' _ "${SCRIPT_DIR}/_common.sh"

cat >"${TEMPORARY_DIRECTORY}/playwright-pass.json" <<'JSON'
{"stats":{"expected":4,"skipped":0,"unexpected":0,"flaky":0}}
JSON
node "${SCRIPT_DIR}/check-playwright-result.mjs" "${TEMPORARY_DIRECTORY}/playwright-pass.json" >/dev/null
cat >"${TEMPORARY_DIRECTORY}/playwright-skipped.json" <<'JSON'
{"stats":{"expected":0,"skipped":4,"unexpected":0,"flaky":0}}
JSON
expect_failure "skipped authenticated Playwright result" \
  node "${SCRIPT_DIR}/check-playwright-result.mjs" "${TEMPORARY_DIRECTORY}/playwright-skipped.json"
cat >"${TEMPORARY_DIRECTORY}/playwright-count-drift.json" <<'JSON'
{"stats":{"expected":3,"skipped":0,"unexpected":0,"flaky":0}}
JSON
expect_failure "authenticated Playwright count drift" \
  node "${SCRIPT_DIR}/check-playwright-result.mjs" "${TEMPORARY_DIRECTORY}/playwright-count-drift.json"

expect_failure "rollback hook inside the candidate repository" \
  env -i PATH="$PATH" ROTRACK_INSPECT_RELEASE_HOOK="${SCRIPT_DIR}/staging-smoke.sh" \
    bash -c 'source "$1"; release_validate_external_executable ROTRACK_INSPECT_RELEASE_HOOK' _ \
    "${SCRIPT_DIR}/_common.sh"

: >"${TEMPORARY_DIRECTORY}/insecure-state.json"
chmod 0644 "${TEMPORARY_DIRECTORY}/insecure-state.json"
expect_failure "group-readable authenticated storage state" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-reviewed \
    ROTRACK_STAGING_FRONTEND_URL=https://frontend.staging.example.test \
    ROTRACK_STAGING_API_URL=https://api.staging.example.test/api/v1 \
    ROTRACK_E2E_USER_A_STORAGE_STATE="${TEMPORARY_DIRECTORY}/insecure-state.json" \
    ROTRACK_E2E_USER_B_STORAGE_STATE="${TEMPORARY_DIRECTORY}/insecure-state.json" \
    bash "${SCRIPT_DIR}/staging-smoke.sh"

expect_failure "rollback rehearsal without explicit approval" \
  env -i PATH="$PATH" \
    ROTRACK_RELEASE_ENVIRONMENT=staging \
    ROTRACK_STAGING_TARGET_ID=staging-reviewed \
    ROTRACK_STAGING_FRONTEND_URL=https://frontend.staging.example.test \
    ROTRACK_STAGING_API_URL=https://api.staging.example.test/api/v1 \
    bash "${SCRIPT_DIR}/rollback-rehearsal.sh"

# The example is intentionally inert and contains no assignment that resembles
# a bearer token, cookie, password, or private key.
if grep -Ein -- '(^|_)(PASSWORD|TOKEN|COOKIE|SECRET|PRIVATE_KEY)=' \
  "${SCRIPT_DIR}/staging.env.example"; then
  printf 'ERROR: secret-like assignment found in staging.env.example\n' >&2
  exit 1
fi

require_text() {
  local file="$1"
  local pattern="$2"
  grep -Eq -- "$pattern" "$file" || {
    printf 'ERROR: required contract is missing from %s\n' "$file" >&2
    exit 1
  }
}

RELEASE_RUNBOOK="${REPOSITORY_ROOT}/docs/operations/release/release-runbook.md"
MONITORING_CONTRACT="${REPOSITORY_ROOT}/docs/operations/monitoring/monitoring-contract.md"
LOGGING_CONTRACT="${REPOSITORY_ROOT}/docs/operations/monitoring/structured-logging.md"
INCIDENT_RUNBOOK="${REPOSITORY_ROOT}/docs/operations/incidents/incident-response.md"
for file in "$RELEASE_RUNBOOK" "$MONITORING_CONTRACT" "$LOGGING_CONTRACT" "$INCIDENT_RUNBOOK"; do
  [[ -s "$file" ]] || {
    printf 'ERROR: expected operations contract is missing or empty: %s\n' "$file" >&2
    exit 1
  }
done

require_text "$RELEASE_RUNBOOK" 'migration.*first|database migration first'
require_text "$RELEASE_RUNBOOK" 'Migration rollback limits'
require_text "$RELEASE_RUNBOOK" 'One `STOP` or `NOT RUN` means no production promotion'
for signal in liveness readiness latency 5xx restarts auth-failures connections migration-status exceptions; do
  require_text "$MONITORING_CONTRACT" "$signal"
done
require_text "$MONITORING_CONTRACT" 'suggested initial threshold'
for field in 'correlation.id' 'http.route' 'http.response.status_code' 'duration_ms'; do
  require_text "$LOGGING_CONTRACT" "$field"
done
for forbidden in Authorization Cookie 'query strings' credentials reflections 'request and response bodies'; do
  require_text "$LOGGING_CONTRACT" "$forbidden"
done
require_text "$INCIDENT_RUNBOOK" 'Incident commander'
require_text "$INCIDENT_RUNBOOK" 'SEV-1'
require_text "${SCRIPT_DIR}/staging-smoke.sh" 'ROTRACK_E2E_REQUIRE_AUTH=1'
require_text "${SCRIPT_DIR}/staging-smoke.sh" 'ROTRACK_E2E_EXPECTED_API_URL'
require_text "${SCRIPT_DIR}/staging-smoke.sh" 'check-playwright-result.mjs'
require_text "${SCRIPT_DIR}/staging-smoke.sh" '/health'
require_text "${SCRIPT_DIR}/staging-smoke.sh" '/readiness'

printf 'PASS: release safeguard syntax, static policy, negative checks, and required contracts completed\n'
