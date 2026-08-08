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

## Remaining M2.2 evidence

M2.2 remains **In progress** until the following additional live checks pass against a clean start:

1. readiness dependency-failure → sanitized `503 {"status":"not_ready"}` while liveness remains 200;
2. the final clean-environment startup is repeated from the documented runbook.

The allowed and denied live CORS preflights passed on 2026-08-07.

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
