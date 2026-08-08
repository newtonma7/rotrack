# Local startup and configuration verification — 2026-08-07

All identifiers and values are redacted. No production access or deployment occurred.

## Frontend

```text
Command: cd frontend && npm run dev -- -p 3100
Result: PASS — Next.js ready; GET / returned HTTP 200
Cleanup: development process stopped
```

## Backend configuration and probes

Deterministic tests under Temurin Java 21 cover:

- environment binding into the actual Hikari data source;
- exact CORS origin validation and preflight behavior;
- JWT issuer/JWKS/audience configuration;
- managed JDBC `verify-full` plus explicit CA-path enforcement;
- loopback-only plaintext exception under the explicit `local` profile;
- independent public liveness and sanitized database readiness;
- readiness result caching/single-flight pool protection.

The configured development backend environment was then corrected to use the dedicated `rotrack_runtime` role, a quoted JDBC URL, and the official CA path:

```text
Command: cd backend && mvn spring-boot:run  # Java 21; ignored environment injected
Result: PASS — application started and was stopped after probes
Health: HTTP 200 {"status":"ok"}
Readiness: HTTP 200 {"status":"ready"}
CORS allowed origin: HTTP 200 with exact `Access-Control-Allow-Origin: http://localhost:3000`, credentials, methods, and headers
CORS denied origin: HTTP 403 with no `Access-Control-Allow-Origin`
Database: TLS 1.3; PostgreSQL 17.6; runtime role privilege audit passed
Sensitive values: not printed
```

Earlier attempts correctly failed closed because the URL was unquoted in the shell environment and then because `$HOME` was passed literally inside the JDBC value. The local ignored `.env` now uses a quoted URL and an absolute CA path. PostgreSQL JDBC 42.7.x treats `sslrootcert=system` as a filename; the official provider CA is required.

## M2.2 completion evidence

The previously open dependency-failure and final clean-start checks are recorded
below under 2026-08-08. The allowed and denied live CORS preflights passed on
2026-08-07.

## Remaining-gate execution — 2026-08-08

### Empty-database migration apply

Docker/Podman was unavailable, so a temporary PostgreSQL 18.4 server was
installed under `/tmp`, initialized as an isolated local cluster, and removed
afterward. No repository or configured development database was used.

```text
Command: ROTRACK_TEST_DATABASE_MODE=apply mvn -Drotrack.postgres.integration=true -Dtest=PostgresMigrationIntegrationTest test
Result: PASS — 1 test, 0 failures/errors/skips; BUILD SUCCESS
Cleanup: temporary PostgreSQL cluster stopped and removed
```

### Direct Supabase Data API RLS matrix

Using refreshed, external storage states (tokens never printed), the real
Supabase REST Data API returned:

```text
User A owned read: PASS (18 rows; every row owned by A)
User B owned read: PASS (0 rows; every returned row owned by B)
User A foreign read: PASS (0 rows)
User B foreign read: PASS (0 rows)
Forged insert as A/B with the other user's user_id: PASS (403 for both)
```

### Fresh signup attempt

A fresh disposable signup through the real `/signup` UI returned HTTP 200 and
navigated to `/signup/confirmation`; Supabase reported a confirmation email was
sent. A redacted Supabase management SQL query then confirmed both the new
`auth.users` row and its matching `public.users` profile. The disposable row was
removed afterward. The confirmation email itself was not opened because no
inbox access was available; existing confirmed disposable users cover the real
sign-in path.

### Readiness dependency failure

With an isolated loopback probe configuration (`ddl-auto=none`, explicit
PostgreSQL dialect, and JDBC metadata disabled), an unreachable local database
produced the intended sanitized result:

```text
Liveness: HTTP 200 {"status":"ok"}
Readiness: HTTP 503 {"status":"not_ready"}
```

The normal `ddl-auto=validate` configuration intentionally fails startup when
the database is unavailable before JPA can initialize; this fail-fast behavior
was also observed and is distinct from the degraded readiness probe mode.

### Final clean validation

```text
Frontend: npm ci, npm audit --audit-level=high, lint, typecheck, Vitest 11/11, build — PASS
Backend: Java 21 mvn clean test — PASS (64 tests, 0 failures, 4 expected opt-in skips)
Backend: mvn package — PASS
Live backend: health 200, readiness 200, services stopped after validation
Authenticated browser: 4 Chromium tests, 0 failures/skips — PASS
```

## Authenticated browser verification — 2026-08-08

Two existing disposable development users signed in through the real frontend and
provided storage states outside the repository:

```text
State files: ~/.local/state/rotrack-e2e/user-a.json and user-b.json
Credentials: not recorded
Command: cd frontend && ROTRACK_E2E_REQUIRE_AUTH=1 npm run e2e
Result: PASS — 4 Chromium tests passed, 0 failed, 0 skipped; 47.5 seconds
```

Covered Work and Rot start/stop, reload/navigation restoration, browser-context
close/reopen restoration, exact dashboard deltas, and User B's inability to
restore, stop, or aggregate User A's Work and Rot sessions. Storage-state files
remain outside the repository with restrictive permissions.
