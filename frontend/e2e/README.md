# Playwright authenticated flow

This directory contains the browser-level critical path without storing real
credentials or committed browser state.

## Setup

1. Start the frontend and API using the repository runbook.
2. Choose a path outside the repository and export it:

```bash
export ROTRACK_E2E_STORAGE_STATE=/tmp/rotrack-auth-state.json
export ROTRACK_E2E_BASE_URL=http://localhost:3000
```

3. Open Playwright's interactive browser, complete sign-in manually with an
isolated development Supabase account, then close the browser to save state:

```bash
npx playwright codegen \
  --save-storage="$ROTRACK_E2E_STORAGE_STATE" \
  "$ROTRACK_E2E_BASE_URL/signin"
```

Do not commit that file. It contains authenticated cookies/local storage.

Install the browser once:

```bash
npx playwright install chromium
```

Run the critical path:

```bash
npm run e2e
```

Without `ROTRACK_E2E_STORAGE_STATE`, the test is intentionally skipped rather
than attempting a real sign-in or silently using production credentials. The
storage state should belong to an isolated development Supabase project.
