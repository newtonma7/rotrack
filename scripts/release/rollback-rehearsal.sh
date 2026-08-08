#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/release/_common.sh
source "${SCRIPT_DIR}/_common.sh"

if [[ "${1:-}" == "--help" ]]; then
  cat <<'USAGE'
Usage: rollback-rehearsal.sh

Verifies the currently deployed candidate, runs staging smoke, invokes an
operator-owned deployment hook to restore the prior immutable application
release, waits for that release, and reruns staging smoke. It never changes or
rolls back the database. See the release runbook before use.
USAGE
  exit 0
fi
[[ $# -eq 0 ]] || release_die "unexpected arguments; use --help"

release_validate_staging_context
[[ "${ROTRACK_ROLLBACK_REHEARSAL_APPROVED:-}" == "YES_STAGING_ONLY" ]] || \
  release_die "ROTRACK_ROLLBACK_REHEARSAL_APPROVED must be exactly YES_STAGING_ONLY"
release_validate_identifier ROTRACK_CANDIDATE_RELEASE_ID
release_validate_identifier ROTRACK_ROLLBACK_RELEASE_ID
[[ "$ROTRACK_CANDIDATE_RELEASE_ID" != "$ROTRACK_ROLLBACK_RELEASE_ID" ]] || \
  release_die "candidate and rollback release identifiers must differ"

release_validate_external_executable ROTRACK_INSPECT_RELEASE_HOOK
release_validate_external_executable ROTRACK_DEPLOY_RELEASE_HOOK

DEPLOY_TIMEOUT_SECONDS="${ROTRACK_DEPLOY_TIMEOUT_SECONDS:-600}"
[[ "$DEPLOY_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] &&
  (( DEPLOY_TIMEOUT_SECONDS >= 30 && DEPLOY_TIMEOUT_SECONDS <= 3600 )) ||
  release_die "ROTRACK_DEPLOY_TIMEOUT_SECONDS must be an integer from 30 to 3600"

inspect_release() {
  # Hook stdout is captured and never echoed; stderr is suppressed so a broken
  # provider wrapper cannot copy credential-bearing diagnostics into evidence.
  "$ROTRACK_INSPECT_RELEASE_HOOK" "$ROTRACK_STAGING_TARGET_ID" 2>/dev/null
}

observed_release="$(inspect_release)" || release_die "could not inspect the deployed staging release"
[[ "$observed_release" == "$ROTRACK_CANDIDATE_RELEASE_ID" ]] || \
  release_die "the deployed staging release is not the approved candidate"

printf 'PASS: approved candidate is currently deployed; running baseline smoke.\n'
"${SCRIPT_DIR}/staging-smoke.sh"

printf 'Invoking the staging-only rollback hook; hook output is suppressed by the no-secrets policy.\n'
"$ROTRACK_DEPLOY_RELEASE_HOOK" "$ROTRACK_STAGING_TARGET_ID" "$ROTRACK_ROLLBACK_RELEASE_ID" \
  >/dev/null 2>&1 || release_die "rollback deployment hook failed; stop and invoke the incident process"

start_seconds="$SECONDS"
while true; do
  observed_release="$(inspect_release)" || true
  if [[ "$observed_release" == "$ROTRACK_ROLLBACK_RELEASE_ID" ]]; then
    break
  fi
  (( SECONDS - start_seconds < DEPLOY_TIMEOUT_SECONDS )) || \
    release_die "rollback release did not become current before the timeout; stop and invoke the incident process"
  sleep 10
done

printf 'PASS: prior application release is current; running post-rollback smoke.\n'
"${SCRIPT_DIR}/staging-smoke.sh" || \
  release_die "post-rollback smoke failed; leave staging blocked and invoke the incident process"

printf 'PASS: staging application rollback rehearsal completed. Database state was not changed.\n'
printf 'STOP: staging remains on the prior release; redeploy the candidate only through the normal approved rollout.\n'
