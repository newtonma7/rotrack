# Authenticated Playwright critical path

This suite exercises the real browser → Supabase Auth → Spring API path. It does
not mock M1.4 dashboard behavior. It verifies Work and Rot mutations,
reload/navigation/browser-reopen restoration, explicit stop, owner dashboard
aggregation, and a two-user ownership boundary.

## Safety and environment contract

Use two **distinct, disposable users** in an isolated development or staging
Supabase project. The suite stops stale active sessions owned by those users and
creates completed sessions; do not point it at production or personal accounts.
Credentials and Playwright storage-state files must remain outside the repository.
Storage state contains reusable authentication material.

Required for the complete suite:

```bash
export ROTRACK_E2E_BASE_URL=http://localhost:3000
# Required for release smoke so browser API responses are bound to the approved API:
export ROTRACK_E2E_EXPECTED_API_URL=http://localhost:8080/api/v1
export ROTRACK_E2E_USER_A_STORAGE_STATE=/tmp/rotrack-e2e-user-a.json
export ROTRACK_E2E_USER_B_STORAGE_STATE=/tmp/rotrack-e2e-user-b.json
```

`ROTRACK_E2E_STORAGE_STATE` remains a compatibility alias for User A. Required-auth runs also require `ROTRACK_E2E_EXPECTED_API_URL`; the harness installs a browser request guard before navigation so `/api/v1` requests to any other origin/base are aborted before transmission. A configured
path that is missing, not a regular file, or resolves inside the repository is a
configuration error. The hostile two-user request is derived from the same API
origin observed on the frontend's successful start request, preventing a false
pass against a different backend.

## Provision external auth state

Start the frontend and backend using the repository runbook. For each disposable
user, launch codegen, sign in through the real `/signin` form, wait for the
dashboard, and then close codegen so it writes the state:

```bash
npx playwright codegen \
  --save-storage="$ROTRACK_E2E_USER_A_STORAGE_STATE" \
  "$ROTRACK_E2E_BASE_URL/signin"

npx playwright codegen \
  --save-storage="$ROTRACK_E2E_USER_B_STORAGE_STATE" \
  "$ROTRACK_E2E_BASE_URL/signin"

chmod 600 "$ROTRACK_E2E_USER_A_STORAGE_STATE" "$ROTRACK_E2E_USER_B_STORAGE_STATE"
```

Never paste passwords, access tokens, storage-state JSON, or complete environment
files into source, command output, CI logs, screenshots, or evidence. Refresh an
expired state by repeating codegen. Playwright tracing and video are disabled
because authenticated network artifacts can retain bearer headers; failure
screenshots remain under ignored `test-results/`.

Install Chromium once:

```bash
npx playwright install chromium
```

## Run and gating behavior

List the suite without credentials or running services:

```bash
npm run e2e -- --list
```

Run configured tests locally:

```bash
npm run e2e
```

When auth configuration is absent, affected tests are reported as explicitly
skipped. This quarantine is owned by the environment operator: the reason is that
real Supabase users and services are external to the source checkout. It is not a
CI success contract. Required authenticated jobs must fail fast on missing User A or User B
configuration:

```bash
ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e
```

A checked critical-path run therefore uses `ROTRACK_E2E_REQUIRE_AUTH=1` and expects
all four Chromium tests to pass. HTML and failure artifacts are ignored by Git;
treat them as sensitive operational output and remove them according to the CI
retention policy.
