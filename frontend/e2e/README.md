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

The gated disposable signup flow additionally accepts fresh, disposable signup
accounts:

```bash
export ROTRACK_E2E_SIGNUP_EMAIL_A='<DISPOSABLE_A_EMAIL>'
export ROTRACK_E2E_SIGNUP_EMAIL_B='<DISPOSABLE_B_EMAIL>'
export ROTRACK_E2E_SIGNUP_PASSWORD='<ENTER_PRIVATELY>'
export ROTRACK_E2E_REQUIRE_SIGNUP=1
```

Set `ROTRACK_E2E_SIGNUP_USERNAME` to make the username deterministic; otherwise
the test generates one. To cover confirmation and subsequent sign-in, provide the
confirmation link privately through `ROTRACK_E2E_SIGNUP_CONFIRMATION_URL` and set
`ROTRACK_E2E_REQUIRE_SIGNUP_CONFIRMATION=1`. The link and password must never be
printed, committed, or included in browser artifacts.

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

A checked authenticated lifecycle run therefore uses `ROTRACK_E2E_REQUIRE_AUTH=1`
and expects all four lifecycle Chromium tests to pass. A checked signup run also
sets `ROTRACK_E2E_REQUIRE_SIGNUP=1`; the full confirmation/sign-in acceptance adds
`ROTRACK_E2E_REQUIRE_SIGNUP_CONFIRMATION=1` and expects both signup tests to pass.
HTML and failure artifacts are ignored by Git; treat them as sensitive
operational output and remove them according to the CI retention policy.

## M5.1 local Notes acceptance

`m51-notes-api.spec.ts` is an authenticated API/browser-boundary acceptance
matrix. It uses the real Supabase access token from each approved storage state,
but every application mutation is sent to the disposable local Spring API. The
spec refuses non-loopback frontend/API URLs, installs the API-target guard, and
covers Note CRUD, opaque pagination, attachment filters, active/completed entry
attachments, move/detach, two-user ownership, foreign attachments, Time Entry
delete detachment/counts, idempotent replay/tombstones, and optimistic conflicts.

Run it only after seeding the two storage-state subjects into a disposable local
PostgreSQL database and starting the local stack:

```bash
ROTRACK_E2E_BASE_URL=http://localhost:<FRONTEND_PORT> \
ROTRACK_E2E_EXPECTED_API_URL=http://localhost:<API_PORT>/api/v1 \
ROTRACK_E2E_USER_A_STORAGE_STATE=/home/newton/.local/state/rotrack-e2e/shared-hosted-production/user-a.json \
ROTRACK_E2E_USER_B_STORAGE_STATE=/home/newton/.local/state/rotrack-e2e/shared-hosted-production/user-b.json \
ROTRACK_E2E_REQUIRE_AUTH=1 \
npm run e2e -- m51-notes-api.spec.ts --project=chromium
```

Keep the storage states, local environment values, browser reports, screenshots,
traces, and server logs outside source control; remove them after the run. Do
not run this spec against hosted application APIs or a non-disposable database.
