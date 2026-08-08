# Remaining verification checklist

Use this checklist to finish the current disposable development database verification. Do **not** use these steps against production. A separate Supabase project/database should be created later for staging and production.

## Safety rules

- Never commit or paste passwords, bearer tokens, Supabase service-role keys, CA contents, or Playwright storage-state JSON.
- Keep `backend/.env` and `frontend/.env` local and ignored.
- Use disposable test users and database rows only.
- Database verification tests roll back their probe data. Still confirm the target is disposable before running them.
- Do not change grants or roles in production.

## Verification status — 2026-08-08

**Not fully complete.** The following checks have passing evidence:

- official Supabase CA and strict managed PostgreSQL TLS configuration;
- dedicated `rotrack_runtime` role audit and live backend startup;
- liveness, readiness, and allowed/denied CORS probes;
- two external disposable-user storage states outside the repository;
- required-authenticated Playwright suite: 4 Chromium tests passed, 0 skipped;
- frontend lint and typecheck.

The remaining gates are:

- empty-database migration `apply` mode;
- direct Supabase Data API two-user RLS matrix;
- fresh signup/confirmation and signup-trigger evidence;
- readiness behavior when the database dependency fails;
- one final clean run from the documented environment contract.

The authenticated browser run used existing disposable users and proves real
sign-in plus the Spring ownership flow; it does not prove fresh signup or direct
Data API RLS.

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

GRANT SELECT, INSERT, UPDATE
  ON TABLE public.time_entries
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
  NOT has_table_privilege(current_user, 'public.time_entries', 'DELETE') AS cannot_delete,
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

export JAVA_HOME=/home/newton/.local/jdk-21.0.12+8
export PATH="$JAVA_HOME/bin:$PATH"

cd /home/newton/dev/rotrack/backend
mvn -Drotrack.postgres.integration=true \
  -Dtest=PostgresMigrationIntegrationTest \
  test
```

Expected result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Stop the disposable database:

```bash
docker stop rotrack-postgres-test
```

---

## 6. Start the backend and frontend

### Backend

```bash
cd /home/newton/dev/rotrack
set -a
source backend/.env
set +a

export JAVA_HOME=/home/newton/.local/jdk-21.0.12+8
export PATH="$JAVA_HOME/bin:$PATH"

cd backend
mvn spring-boot:run
```

### Frontend

In a second terminal:

```bash
cd /home/newton/dev/rotrack/frontend
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

Allowed origin:

```bash
curl --include --request OPTIONS \
  --header 'Origin: http://localhost:3000' \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type' \
  http://localhost:8080/api/v1/time-entries/start
```

This should include:

```text
Access-Control-Allow-Origin: http://localhost:3000
```

An unconfigured origin must not receive `Access-Control-Allow-Origin`:

```bash
curl --include --request OPTIONS \
  --header 'Origin: https://attacker.example' \
  --header 'Access-Control-Request-Method: POST' \
  http://localhost:8080/api/v1/time-entries/start
```

---

## 7. Create two disposable users

1. Open `http://localhost:3000/signup`.
2. Create User A.
3. Complete email confirmation if enabled.
4. Sign out.
5. Create User B.
6. Complete email confirmation if enabled.

Verify that the signup trigger created both profiles using an administrative SQL connection:

```sql
SELECT count(*)
FROM public.users
WHERE email IN ('USER_A_EMAIL', 'USER_B_EMAIL');
```

Expected count: `2`.

Do not record the real email addresses in committed evidence.

---

## 8. Create Playwright storage states

```bash
mkdir -p ~/.local/state/rotrack-e2e
chmod 700 ~/.local/state/rotrack-e2e

export ROTRACK_E2E_BASE_URL=http://localhost:3000
export ROTRACK_E2E_USER_A_STORAGE_STATE="$HOME/.local/state/rotrack-e2e/user-a.json"
export ROTRACK_E2E_USER_B_STORAGE_STATE="$HOME/.local/state/rotrack-e2e/user-b.json"
```

For User A:

```bash
cd /home/newton/dev/rotrack/frontend
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
cd /home/newton/dev/rotrack/frontend
ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e
```

Expected: all four Chromium tests pass with no skips.

The scenarios cover:

- Work start, reload/navigation, explicit stop, and dashboard delta;
- Rot start, explicit stop, and dashboard delta;
- browser-context close/reopen restoration;
- User B isolation for active sessions, stop requests, totals, daily buckets, Work, and Rot.

---

## 10. Run the final local validation

Frontend:

```bash
cd /home/newton/dev/rotrack/frontend
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
export JAVA_HOME=/home/newton/.local/jdk-21.0.12+8
export PATH="$JAVA_HOME/bin:$PATH"

cd /home/newton/dev/rotrack/backend
mvn clean test
mvn package
```

Then run:

```bash
cd /home/newton/dev/rotrack
git diff --check
git status --short
```

Only record redacted results in `todo.md`.

---

## 11. Send the completion handoff

After completing the steps, provide only paths and yes/no values:

```text
Current database confirmed disposable: yes
Official CA path: /absolute/path/to/ca.crt
Application role configured: yes
User A storage-state path: /absolute/path/user-a.json
User B storage-state path: /absolute/path/user-b.json
Authorize rollback-only RLS/API verification: yes
```

Do not include credentials, tokens, storage-state contents, or certificate contents.

---

## 12. After M2 is complete: staging and production

Do not start deployment until M2 passes.

For staging, create a separate Supabase project and configure:

- a separate database CA certificate;
- a separate least-privilege `rotrack_app` role;
- separate disposable/staging users;
- staging frontend CORS origin;
- Vercel frontend configuration;
- ECS/Fargate backend configuration;
- CloudWatch health, latency, error-rate, restart, and connection monitoring.

Production must use another separate Supabase project and repeat the CA, role, migration, RLS, browser, health, and rollback verification process.
