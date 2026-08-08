#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/release/_common.sh
source "${SCRIPT_DIR}/_common.sh"

if [[ "${1:-}" == "--help" ]]; then
  cat <<'USAGE'
Usage: staging-smoke.sh

Runs public liveness/readiness probes, a frontend availability probe, and the
required-authenticated Playwright boundary against an explicitly identified
staging target. Configuration is environment-only; see staging.env.example.
USAGE
  exit 0
fi
[[ $# -eq 0 ]] || release_die "unexpected arguments; use --help"

release_validate_staging_context
release_validate_storage_state ROTRACK_E2E_USER_A_STORAGE_STATE
release_validate_storage_state ROTRACK_E2E_USER_B_STORAGE_STATE
[[ ! "$ROTRACK_E2E_USER_A_STORAGE_STATE" -ef "$ROTRACK_E2E_USER_B_STORAGE_STATE" ]] || \
  release_die "User A and User B must use distinct staging storage-state files"
release_require_command curl
release_require_command npm
release_require_command node

SMOKE_TIMEOUT_SECONDS="${ROTRACK_SMOKE_TIMEOUT_SECONDS:-15}"
[[ "$SMOKE_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] &&
  (( SMOKE_TIMEOUT_SECONDS >= 1 && SMOKE_TIMEOUT_SECONDS <= 120 )) ||
  release_die "ROTRACK_SMOKE_TIMEOUT_SECONDS must be an integer from 1 to 120"

TEMPORARY_DIRECTORY="$(mktemp -d)"
trap 'rm -rf -- "$TEMPORARY_DIRECTORY"' EXIT

probe_json() {
  local name="$1"
  local url="$2"
  local expected_status="$3"
  local expected_body_pattern="$4"
  local body_file="${TEMPORARY_DIRECTORY}/${name}.body"
  local status

  status="$(curl --silent --show-error \
    --connect-timeout "$SMOKE_TIMEOUT_SECONDS" \
    --max-time "$SMOKE_TIMEOUT_SECONDS" \
    --output "$body_file" \
    --write-out '%{http_code}' \
    "$url")" || release_die "${name} probe could not complete"

  [[ "$status" == "$expected_status" ]] || release_die "${name} probe returned an unexpected status"
  grep -Eq "$expected_body_pattern" "$body_file" || release_die "${name} probe returned an unexpected body"
  printf 'PASS: %s returned the expected public status contract\n' "$name"
}

probe_status() {
  local name="$1"
  local url="$2"
  local expected_status="$3"
  local status

  status="$(curl --silent --show-error \
    --connect-timeout "$SMOKE_TIMEOUT_SECONDS" \
    --max-time "$SMOKE_TIMEOUT_SECONDS" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$url")" || release_die "${name} probe could not complete"

  [[ "$status" == "$expected_status" ]] || release_die "${name} probe returned an unexpected status"
  printf 'PASS: %s returned the expected public status contract\n' "$name"
}

API_BASE="${ROTRACK_STAGING_API_URL%/}"
probe_json "liveness" "${API_BASE}/health" "200" '^[[:space:]]*\{[[:space:]]*"status"[[:space:]]*:[[:space:]]*"ok"[[:space:]]*\}[[:space:]]*$'
probe_json "readiness" "${API_BASE}/readiness" "200" '^[[:space:]]*\{[[:space:]]*"status"[[:space:]]*:[[:space:]]*"ready"[[:space:]]*\}[[:space:]]*$'
probe_status "frontend" "${ROTRACK_STAGING_FRONTEND_URL%/}/signin" "200"

printf 'Running the required-authenticated Playwright boundary (storage paths and contents are not printed).\n'
(
  cd -- "${RELEASE_REPOSITORY_ROOT}/frontend"
  export ROTRACK_E2E_BASE_URL="${ROTRACK_STAGING_FRONTEND_URL%/}"
  export ROTRACK_E2E_EXPECTED_API_URL="$API_BASE"
  export ROTRACK_E2E_REQUIRE_AUTH=1
  export PLAYWRIGHT_JSON_OUTPUT_FILE="${TEMPORARY_DIRECTORY}/playwright-results.json"
  npm run e2e -- --reporter=json --output="${TEMPORARY_DIRECTORY}/playwright-output"
)

node "${SCRIPT_DIR}/check-playwright-result.mjs" "${TEMPORARY_DIRECTORY}/playwright-results.json"

printf 'PASS: staging smoke completed against the inventory-bound frontend and API targets.\n'
