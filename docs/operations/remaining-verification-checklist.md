# Remaining verification checklist

Use this checklist to finish the current disposable development database verification. Do **not** use these steps against production. The approved target keeps development and approved environment-scoped authenticated E2E on this existing non-production/dev Supabase Free project; credential-free PR CI uses isolated disposable PostgreSQL instead of hosted Supabase. Production is the separate `rotrack-prod` Supabase Free project. No deployment is implied by this local checklist.

## Safety rules

- Never commit or paste passwords, bearer tokens, Supabase service-role keys, CA contents, or Playwright storage-state JSON.
- Keep `backend/.env` and `frontend/.env` local and ignored.
- Use disposable test users and database rows only.
- Database verification tests roll back their probe data. Still confirm the target is disposable before running them.
- Do not change grants or roles in production.
- Set local paths explicitly instead of copying a developer-specific home directory:

```bash
export REPO_ROOT='/absolute/path/to/rotrack'
export JAVA_HOME='/absolute/path/to/java-21'
export PATH="$JAVA_HOME/bin:$PATH"
```

## Verification status — 2026-08-12

**Not fully complete.** The following checks have passing evidence:

- official Supabase CA and strict managed PostgreSQL TLS configuration;
- dedicated `rotrack_runtime` role audit and live backend startup;
- liveness, readiness, and allowed/denied CORS probes;
- empty-database migration `apply` mode against a temporary isolated PostgreSQL cluster;
- direct Supabase Data API two-user RLS matrix, including forged-insert denial;
- two external disposable-user storage states outside the repository;
- required-authenticated hosted smoke: 4 Chromium tests passed, 0 skipped, unexpected, or flaky, with API-target binding;
- degraded dependency-failure probe: liveness 200 and sanitized readiness 503;
- final frontend/backend validation and clean configured health/readiness run;
- isolated migrations 001–005 plus local authenticated M4 acceptance for preferences and completed history, including two-user isolation, pagination, overlap rejection, active-entry exclusion, and create/edit/delete.

The fresh signup trigger gate passes through a redacted administrative
read-only query confirming matching `auth.users` and `public.users` rows. The
2026-08-11 hosted smoke passed the authenticated `4/4` contract and API-target
binding. Operator-owned synthetic accounts and stopped rows remain by
product-owner decision; cleanup is not claimed. No password or account details
are recorded.

---

## 1. Download the official Supabase CA certificate

1. Open the Supabase dashboard for the current development project.
2. Go to **Project Settings → Database → SSL Configuration**.
3. Download the database CA certificate.
4. Store it outside the repository:

```bash
mkdir -p ~/.config/rotrack
mv ~/Downloads/prod-ca-*.crt ~/.config/rotrack/supabase-db-ca.crt
chmod 600 ~/.config/rotrack/supabase-db-ca.crt
```

Supabase reference: <https://supabase.com/docs/guides/platform/ssl-enforcement>

Do not capture a certificate with `openssl s_client` and trust it manually.

---

## 2. Enable database SSL enforcement

In **Database Settings → SSL Configuration**, enable:

> Enforce SSL on incoming connections

Keep the downloaded CA certificate available before enabling this setting.

---

## 3. Configure strict JDBC TLS

Use the connection details from the Supabase **Connect** dialog. Prefer the **Session pooler** connection unless the direct database endpoint is available from your network.

Edit the ignored file `backend/.env`:

```dotenv
DATABASE_URL='jdbc:postgresql://POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=/home/YOUR_USER/.config/rotrack/supabase-db-ca.crt'
DATABASE_USERNAME=ADMIN_OR_CURRENT_ROLE
DATABASE_PASSWORD=LOCAL_ONLY_PASSWORD
```

Use the exact host, port, username, and password supplied by Supabase. Do not put the password in `DATABASE_URL`.

---

## 4. Create the least-privilege application role

Connect to PostgreSQL using an administrative connection from the Supabase **Connect** dialog. Use `verify-full` and the downloaded CA certificate.

In `psql`, run:

```sql
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime'
  ) THEN
    CREATE ROLE rotrack_runtime
      LOGIN
      INHERIT
      BYPASSRLS
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOREPLICATION;
  END IF;
END
$$;

ALTER ROLE rotrack_runtime
  WITH LOGIN
       INHERIT
       BYPASSRLS
       NOSUPERUSER
       NOCREATEDB
       NOCREATEROLE
       NOREPLICATION;

GRANT CONNECT ON DATABASE postgres TO rotrack_runtime;
GRANT USAGE ON SCHEMA public TO rotrack_runtime;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM rotrack_runtime;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM rotrack_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE public.time_entries
  TO rotrack_runtime;

-- Run this preference grant only after migration 004 has created the table.
GRANT SELECT, INSERT, UPDATE
  ON TABLE public.user_preferences
  TO rotrack_runtime;

-- Run after migration 006: Notes are Spring-only; replay metadata is content-free but API-only.
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE public.notes
  TO rotrack_runtime;
GRANT SELECT, INSERT, UPDATE
  ON TABLE public.note_creation_replays
  TO rotrack_runtime;

GRANT USAGE
  ON TYPE public.activity_type
  TO rotrack_runtime;

COMMIT;
```

Set the role password without putting it in SQL history:

```text
\password rotrack_runtime
```

Then update `backend/.env`:

```dotenv
DATABASE_USERNAME=rotrack_runtime
DATABASE_PASSWORD=ROLE_PASSWORD
```

The role uses `BYPASSRLS` because pooled Spring JDBC connections do not carry the browser JWT into PostgreSQL. Spring ownership-scoped queries remain the application authorization boundary.

### Verify the role

Connect as `rotrack_runtime` and run:

```sql
SELECT
  current_user <> 'postgres' AS dedicated_identity,
  NOT rolsuper AS non_superuser,
  rolbypassrls AS bypass_rls,
  NOT rolcreatedb AS cannot_create_database,
  NOT rolcreaterole AS cannot_create_role,
  NOT rolreplication AS cannot_replicate
FROM pg_roles
WHERE rolname = current_user;

SELECT
  has_table_privilege(current_user, 'public.time_entries', 'SELECT') AS can_select,
  has_table_privilege(current_user, 'public.time_entries', 'INSERT') AS can_insert,
  has_table_privilege(current_user, 'public.time_entries', 'UPDATE') AS can_update,
  has_table_privilege(current_user, 'public.time_entries', 'DELETE') AS can_delete,
  has_table_privilege(current_user, 'public.user_preferences', 'SELECT') AS can_read_preferences,
  has_table_privilege(current_user, 'public.user_preferences', 'INSERT') AS can_create_preferences,
  has_table_privilege(current_user, 'public.user_preferences', 'UPDATE') AS can_update_preferences,
  has_table_privilege(current_user, 'public.notes', 'SELECT') AS can_read_notes,
  has_table_privilege(current_user, 'public.notes', 'INSERT') AS can_create_notes,
  has_table_privilege(current_user, 'public.notes', 'UPDATE') AS can_update_notes,
  has_table_privilege(current_user, 'public.notes', 'DELETE') AS can_delete_notes,
  has_table_privilege(current_user, 'public.note_creation_replays', 'SELECT') AS can_read_note_replays,
  has_table_privilege(current_user, 'public.note_creation_replays', 'INSERT') AS can_create_note_replays,
  has_table_privilege(current_user, 'public.note_creation_replays', 'UPDATE') AS can_update_note_replays,
  NOT has_table_privilege(current_user, 'public.user_preferences', 'DELETE') AS cannot_delete_preferences,
  NOT has_table_privilege(current_user, 'public.time_entries', 'TRUNCATE') AS cannot_truncate,
  NOT has_schema_privilege(current_user, 'public', 'CREATE') AS cannot_create_schema_objects;
```

Every result should be `true`.

---

## 5. Run empty-database migration verification

This step is easiest with temporary PostgreSQL 17. Install Docker or Podman first.

```bash
docker run --detach --rm \
  --name rotrack-postgres-test \
  --publish 55432:5432 \
  --env POSTGRES_PASSWORD=rotrack-test-only \
  postgres:17
```

Wait until it is ready:

```bash
docker exec rotrack-postgres-test pg_isready -U postgres -d postgres
```

Run the migration test:

```bash
export ROTRACK_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/postgres?sslmode=disable'
export ROTRACK_TEST_DATABASE_USERNAME='postgres'
export ROTRACK_TEST_DATABASE_PASSWORD='rotrack-test-only'
export ROTRACK_TEST_DATABASE_ISOLATED='true'
export ROTRACK_TEST_DATABASE_MODE='apply'

test -d "$REPO_ROOT/backend"
test -x "$JAVA_HOME/bin/java"

cd "$REPO_ROOT/backend"
mvn -Drotrack.postgres.integration=true \
  -Dtest=PostgresMigrationIntegrationTest \
  test
```

Expected result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The two tests prove the fail-closed existing-profile probe, migrated username/signup contract, preference defaults/RLS, and completed-history constraints, including the `btree_gist` prerequisite, notes ceiling, and same-user range exclusion. The apply target must be disposable; migrations do not invent usernames, truncate notes, or repair overlapping rows.

Stop the disposable database:

```bash
docker stop rotrack-postgres-test
```

---

## 6. Start the backend and frontend

### Backend

```bash
cd "$REPO_ROOT"
set -a
source backend/.env
set +a

cd backend
mvn spring-boot:run
```

### Frontend

In a second terminal with `REPO_ROOT` set:

```bash
cd "$REPO_ROOT/frontend"
npm ci
npm run dev
```

### Health checks

```bash
curl --silent --show-error --fail-with-body \
  http://localhost:8080/api/v1/health

curl --silent --show-error --fail-with-body \
  http://localhost:8080/api/v1/readiness
```

Expected:

```json
{"status":"ok"}
{"status":"ready"}
```

### CORS checks

Allowed origin for the current ignored local configuration:

```bash
curl --include --request OPTIONS \
  --header 'Origin: http://localhost:3001' \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type' \
  http://localhost:8080/api/v1/time-entries/start
```

This should include:

```text
Access-Control-Allow-Origin: http://localhost:3001
```

The 2026-08-09 recheck returned HTTP 200 with that exact header. A preflight
from `http://localhost:3000` returned HTTP 403 with no allow-origin header.
`CORS_ALLOWED_ORIGINS` and the actual browser origin must match exactly.

An unconfigured origin must not receive `Access-Control-Allow-Origin`:

```bash
curl --include --request OPTIONS \
  --header 'Origin: https://attacker.example' \
  --header 'Access-Control-Request-Method: POST' \
  http://localhost:8080/api/v1/time-entries/start
```

---

## 7. Create two disposable users

1. Open `http://localhost:3001/signup` for the currently configured local origin, or use the exact origin configured in `CORS_ALLOWED_ORIGINS`.
2. Create User A.
3. Complete email confirmation if enabled.
4. Sign out.
5. Create User B.
6. Complete email confirmation if enabled.

Verify that the signup trigger created both profiles using an administrative SQL connection:

```sql
SELECT email, username
FROM public.users
WHERE email IN ('USER_A_EMAIL', 'USER_B_EMAIL');
```

Expected result: two rows with valid, distinct lowercase usernames. Do not
record the real email addresses or usernames in committed evidence.

Do not record the real email addresses in committed evidence.

---

## 8. Create Playwright storage states

```bash
mkdir -p ~/.local/state/rotrack-e2e
chmod 700 ~/.local/state/rotrack-e2e

export ROTRACK_E2E_BASE_URL=http://localhost:3001
export ROTRACK_E2E_USER_A_STORAGE_STATE="$HOME/.local/state/rotrack-e2e/user-a.json"
export ROTRACK_E2E_USER_B_STORAGE_STATE="$HOME/.local/state/rotrack-e2e/user-b.json"
```

For User A:

```bash
cd "$REPO_ROOT/frontend"
npx playwright codegen \
  --save-storage="$ROTRACK_E2E_USER_A_STORAGE_STATE" \
  "$ROTRACK_E2E_BASE_URL/signin"
```

Sign in, wait for the authenticated dashboard, then close codegen.

Repeat for User B:

```bash
npx playwright codegen \
  --save-storage="$ROTRACK_E2E_USER_B_STORAGE_STATE" \
  "$ROTRACK_E2E_BASE_URL/signin"
```

Protect the files:

```bash
chmod 600 \
  "$ROTRACK_E2E_USER_A_STORAGE_STATE" \
  "$ROTRACK_E2E_USER_B_STORAGE_STATE"
```

Never print, open, commit, or send these JSON files.

---

## 9. Run the authenticated browser suite

```bash
cd "$REPO_ROOT/frontend"
ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e
```

Expected: all four Chromium tests pass with no skips.

The scenarios cover:

- Work start, reload/navigation, explicit stop, and dashboard delta;
- Rot start, explicit stop, and dashboard delta;
- browser-context close/reopen restoration;
- User B isolation for active sessions, stop requests, totals, daily buckets, Work, and Rot.

M4's 2026-08-12 focused local browser acceptance additionally verified private preference defaults/persistence/isolation; 20-row history pagination; create/edit/delete; `TIME_ENTRY_OVERLAP`; active-entry exclusion; two-user history isolation; saved-timezone form conversion; and loaded mobile layout without horizontal overflow. That focused run is recorded as evidence rather than a permanent credentialed test because it requires operator-owned external auth state and disposable database setup.

---

## 10. Run the final local validation

Frontend:

```bash
cd "$REPO_ROOT/frontend"
npm ci
npm audit --audit-level=high
npm run lint
npm run typecheck
npm test
npm run build
ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e
```

Backend:

```bash
cd "$REPO_ROOT/backend"
mvn clean test
mvn package
```

Then run:

```bash
cd "$REPO_ROOT"
git diff --check
git status --short
```

Only record redacted results in `todo.md`.

---

## 11. Send the completion handoff

After completing the steps, provide only paths and yes/no values:

```text
Current database confirmed disposable: yes
Official CA: recorded privately
Application role configured: yes
User A/User B storage states: recorded privately
Authorize rollback-only RLS/API verification: yes
```

Do not include credentials, tokens, storage-state contents, or certificate contents.

---

## 12. Non-production exception and production gate

The 2026-08-11 Azure/Vercel candidate checkpoint is recorded in [`azure-nonproduction.md`](azure-nonproduction.md) and [`single-environment.md`](single-environment.md). Public GitHub visibility, the approved protected `main` fields, exact required contexts, guarded-environment policy, empty auth-secret inventories, absent/default-disabled E2E variable, and public-repo security features are read back. M3.1 is Verified. Hosted authenticated `4/4`, public smoke, and corrected exact no-schema-change backend/frontend rollback rehearsal passed. Rate limiting remains explicitly deferred/accepted; ten genuine zero-replica trials completed, but p95 readiness was 39.425 seconds and exceeded the 30-second criterion, so scale-to-zero remains an explicitly accepted risk rather than a verified pass. Collector redaction, alert delivery/receipt, and alert routing evidence remain open. The provider test-notification command failed, so provider-synthetic delivery is **NOT VERIFIED**; one alternate temporary metric-alert path has a confirmed receipt. The backup limitation is accepted as already documented, and production-readiness remains stopped.

For non-production, use the existing shared non-production/dev Supabase Free project with:

- its official database CA and least-privilege `rotrack_runtime` role;
- disposable non-production users/data for development and approved environment-scoped authenticated E2E; credential-free PR CI uses isolated disposable PostgreSQL instead of hosted Supabase;
- Vercel Preview in the one Vercel project;
- logical GitHub `nonproduction` environment, read back as exactly protected `main` with no reviewers or secrets;
- public GitHub `main` protection with pull requests, zero human approvals, strict app-bound required contexts, administrator enforcement, linear history, no force pushes/deletion, and advisory `CODEOWNERS`;
- repository/nonproduction/production auth-secret inventories empty and `ROTRACK_AUTHENTICATED_E2E_ENABLED` absent/default-disabled;
- vulnerability reporting, Dependabot security fixes, secret scanning, push protection, and default CodeQL setup enabled/configured;
- Azure Container Apps Consumption in managed environment `rotrack-nonproduction-env` inside resource group `rotrack-nonproduction` with app `rotrack-api-nonproduction`;
- exact CORS, health/readiness, cold-start, connection, telemetry, and budget/credit-expiry checks.

Production must use the separately created `rotrack-prod` Supabase Free project, Vercel Production in the same Vercel project, logical GitHub `production`, and Azure managed environment `rotrack-production-env` inside resource group `rotrack-production` with app `rotrack-api-production`. Repeat the CA, role, migration, RLS, browser, health, monitoring, budget, and rollback process. These production requirements remain targets only; no production resource is claimed as configured or verified.
