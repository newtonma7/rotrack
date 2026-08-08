# Release and rollback runbook

**Status:** preparation only. Integrated staging does not yet exist, so the smoke and rollback rehearsal have not been executed. This runbook does not authorize a deployment.

This runbook protects the database-first rotrack rollout: Supabase Auth in the browser, a Vercel frontend, an ECS/Fargate Spring API, and Supabase PostgreSQL. It preserves explicit timer sessions and the API ownership boundary. Application rollback must not silently rewrite active or completed sessions.

## Roles and approvals

Replace these placeholders in the restricted release record before a staging rehearsal or production release:

| Role | Named person / rotation | Required action |
|---|---|---|
| Release owner | `[release-owner]` | Owns the checklist, immutable release record, and stop/go call |
| Migration operator | `[migration-operator]` | Reviews and applies ordered SQL; confirms observed schema version |
| Application operator | `[application-operator]` | Deploys/rolls back immutable frontend and backend artifacts |
| Verification owner | `[verification-owner]` | Runs smoke checks and records sanitized results |
| Monitoring owner | `[monitoring-owner]` | Confirms dashboards, alerts, routing, and release annotations |
| Security approver | `[security-approver]` | Reviews trust-boundary, secret, auth, and privacy evidence |
| Incident commander | `[incident-commander]` | Takes control when a stop condition is met |
| Product approver | `[product-approver]` | Approves promotion of the tested artifact |

No person may approve their own unreviewed migration or waive a failed release gate. Production requires explicit release-owner, migration-operator, monitoring-owner, security, and product approvals recorded with timestamps. A release stops when any required approver is unavailable.

## Immutable release record

Create one access-controlled record per release. Use a non-secret release ID such as `rotrack-YYYYMMDD-N`; do not put credentials, tokens, auth storage, private content, response bodies, or complete environment files in it.

Record:

- source commit and passing CI run;
- frontend immutable deployment identifier;
- backend image digest and task-definition revision;
- ordered migration filenames and reviewed checksums;
- current schema version and expected post-migration version;
- compatibility statement for **old app + new schema** and **new app + new schema**;
- staging target/inventory reference and separate production target/inventory reference;
- smoke and rollback-rehearsal evidence references;
- dashboard and alert identifiers from the monitoring contract;
- open defects, risk acceptance, approvals, start/end times, and outcome.

Never identify an artifact only by a mutable tag such as `latest`.

## Required rollout shape

Every database-dependent release uses **expand → migrate → application → contract**. The application deployment described here includes only expand/migrate-safe changes. A destructive contract migration is a later release after every supported application version has stopped reading and writing the old shape.

A migration is rollout-compatible only when all of the following are true:

1. The currently deployed application continues to start (`ddl-auto: validate`) and serve against the migrated schema.
2. Existing reads and writes remain valid while old and new tasks overlap.
3. New required columns have safe defaults or remain nullable until all writers populate them.
4. Renames use additive dual-read/dual-write transitions; tables, columns, indexes, enum values, constraints, and accepted values used by the old version are not removed in this release.
5. Backfills are bounded, restartable, observable, and do not hold unsafe locks for the full dataset.
6. The new application can be rolled back while leaving the migrated schema in place.

If compatibility cannot be proven, stop. Do not compensate with a simultaneous migration/application cutover.

## Preflight gate

The release owner verifies, with separate staging and production inventories:

- CI passed on the exact commit; there are no open critical/high security or data-integrity defects.
- The staging smoke and rollback rehearsal passed for the exact candidate artifacts. A rehearsal of a different digest is not evidence.
- Frontend/API origins, Supabase project, database host/project reference, runtime role, and monitoring environment all resolve to the intended environment. Do not print their secret values.
- Provider backup/PITR status, restore window, database capacity, migration connection reservation, and current connection utilization are acceptable.
- The previous compatible frontend/backend release is still available by immutable identifier.
- Deployment circuit breaker, bounded connection pool, liveness, readiness, exact CORS, TLS CA mount, and secret references are configured and reviewed.
- Migration locks/runtime/backfill behavior and checksums were reviewed; a forward-fix owner and decision deadline are named.
- Incident roles, communication channel, and provider access are staffed for the rollout and observation window.
- The monitoring owner has tested alert routing in the target environment without generating a production incident.
- Approved edge/backend rate limits protect authentication-adjacent and mutation endpoints. Staging evidence covers expected `429` responses, trusted client-identity handling, recovery after the window, and proxy/backend failure behavior without bypass or fail-open exposure. Rate limiting is not implemented yet, so this item is currently **STOP**.

Any mismatch, unavailable rollback artifact, unknown migration state, failed readiness, missing required-auth test configuration, or missing approval is **STOP**.

## Ordered rollout

### 1. Freeze and annotate

1. Announce the release window and freeze unrelated deployments/migrations.
2. Record baseline frontend exceptions, API latency/error rate, auth failures, ready task count/restarts, and database connections.
3. Attach the release ID to deployment and monitoring annotations.

### 2. Apply and verify the database migration first

1. Migration operator rechecks the target identity and observed schema version against the release record.
2. Confirm the old application is compatible with the proposed schema and that rollback leaves the migration applied.
3. Apply only the reviewed ordered migrations using the approved provider path and a separately authorized migration identity. Never use the runtime application's DML role for schema changes.
4. Record only migration name/checksum, timestamps, status, and sanitized error code. Never record connection strings or SQL containing private values.
5. Verify the expected schema version, required constraints/indexes/policies/grants, and signup/runtime ownership boundaries using the approved migration checks.
6. Confirm old tasks remain live and ready and that connection/latency/error alerts remain clear during the observation window.

A failed, partially applied, unexpected, or long-blocking migration is **STOP**. Do not deploy the application. Prefer a reviewed forward fix; see migration rollback limits below.

### 3. Deploy the backend application

1. Deploy the exact backend image digest through a rolling ECS deployment with the circuit breaker enabled.
2. Do not route a task until `GET /api/v1/readiness` returns `200 {"status":"ready"}`. Liveness is independently `GET /api/v1/health` → `200 {"status":"ok"}`.
3. Keep enough old healthy tasks to serve traffic until new tasks are ready. Watch readiness, 5xx, latency, auth failures, restarts/task health, and database connection utilization.
4. Verify the running digest/task-definition revision matches the release record.

Readiness 503, unexpected restart, digest mismatch, ownership/auth regression, elevated 5xx, or connection pressure is **STOP** and triggers application rollback.

### 4. Deploy the frontend application

1. Promote the exact immutable frontend deployment built for the release.
2. Verify its public API URL and Supabase Auth project belong to the same intended environment and CORS allows only the exact frontend origin.
3. Verify the immutable deployment identifier and release annotation.

Do not rebuild during promotion; a new build is a new candidate requiring CI and staging evidence.

### 5. Smoke and observe

1. Run `scripts/release/staging-smoke.sh` in staging before production approval. For production, use an independently approved non-mutating production smoke plan; the tracked authenticated script is staging-only and must never target production.
2. The staging script verifies frontend availability, independent liveness/readiness, and runs Playwright with `ROTRACK_E2E_REQUIRE_AUTH=1`. All four Chromium tests must pass with zero skips.
3. Observe the initial windows in the [monitoring contract](../monitoring/monitoring-contract.md); compare against the recorded baseline.
4. Verification owner and monitoring owner attach sanitized results to the immutable release record.

The authenticated staging suite creates/stops disposable sessions. It must use two staging-only disposable users and external storage-state files. Never run it with personal or production accounts.

## Application rollback

Rollback the application when a stop condition follows a successful compatible migration:

1. Incident commander freezes further deployment and names the rollback decision time.
2. Application operator restores the prior **backend image digest/task-definition revision** and waits for old tasks to become ready before removing failed tasks.
3. Restore the prior **frontend immutable deployment** that targets the same API/schema. If only one tier regressed, the release compatibility matrix must explicitly permit a partial rollback; otherwise restore both as one release.
4. Leave the backward-compatible database migration applied.
5. Run public liveness/readiness and the approved smoke boundary. Confirm task health, errors, latency, auth failures, frontend exceptions, and connections return to baseline.
6. Keep the release blocked, preserve sanitized evidence, and open an incident/problem record. Redeployment is a new stop/go decision.

Do not change timer/session rows during rollback and do not stop users' active sessions. Do not route traffic to unready tasks.

## Migration rollback limits

The ordered SQL migrations do not promise reversible `down` scripts. Application rollback and migration rollback are different operations.

- Additive schema changes normally remain after application rollback.
- Dropped/renamed data, lossy type conversions, enum removal, rewritten timestamps, and destructive backfills cannot be made safe by redeploying an old application.
- Transactional DDL may roll back only if the migration tool reports the transaction failed before commit. Do not assume all provider operations or concurrent index operations share that behavior.
- A manual reverse migration is allowed only after independent review proves it preserves data and remains compatible with the running application. It is applied as a new ordered forward migration, not by editing migration history.
- Point-in-time restore is disaster recovery, not routine rollback: it creates an outage/cutover problem and can discard writes after the restore point. It requires incident-command, database-owner, security, and product approval plus a reconciliation plan.
- If a migration is committed and breaks both old and new application versions, stop traffic-changing work and choose a reviewed forward fix or disaster recovery. Never improvise destructive SQL during the incident.

## Staging rehearsal

After integrated staging exists, copy `scripts/release/staging.env.example` outside the repository and replace every placeholder from the approved staging inventory. Export its assignments without printing values: `set -a; source /external/path/staging.env; set +a`.

The smoke script fails unless the environment is exactly `staging`; target ID, staging frontend/API URLs, and staging/development/production Supabase refs exactly match an operator-owned mode-`0600` inventory outside the repository; authoritative production frontend/API URLs in that same inventory differ from staging; all three Supabase refs differ; HTTPS non-placeholder URLs are used; and both auth state files are regular files outside the repository. Playwright is also given the approved API base and rejects frontend responses from any other API. Its JSON result and screenshots stay in a temporary mode-restricted directory that is removed on every exit; the script accepts exactly four passed tests and zero skipped, unexpected, or flaky results.

The rollback rehearsal expects the candidate to be currently deployed. Operator-owned hooks have this interface:

```text
inspect hook:  INSPECT_HOOK <staging-target-id> -> exact current release ID on stdout
deploy hook:   DEPLOY_HOOK <staging-target-id> <prior-release-id>
```

A release ID maps to both immutable frontend and backend artifacts in the restricted release inventory. Hooks must validate staging again, use approved provider authentication, emit no secrets, and return nonzero on any partial or uncertain result. The rehearsal:

1. verifies the candidate release ID;
2. runs baseline staging smoke;
3. restores the prior application release only;
4. waits until the prior release ID is observed;
5. reruns staging smoke; and
6. leaves staging on the prior release for an explicit subsequent rollout.

It never applies or reverses a migration. Do not execute either script until integrated staging, disposable users, hooks, approvals, and monitoring exist.

## Stop/go record

Record each gate as `GO`, `STOP`, or `NOT RUN`, with owner, UTC timestamp, and evidence link:

| Gate | Required result |
|---|---|
| Exact CI artifact and migration checksums | GO |
| Backward compatibility / application rollback matrix | GO |
| Separate target and no-secret configuration review | GO |
| Migration apply and schema verification | GO |
| Backend readiness / rolling deployment | GO |
| Frontend immutable promotion / exact CORS | GO |
| Required-auth staging Playwright (4 passed, 0 skipped) | GO |
| Rollback rehearsal for exact candidate/prior pair | GO |
| Alerts, dashboards, routes, and incident staffing | GO |
| Rate limits and `429`/bypass/failure tests | GO |
| Observation window and required approvals | GO |

One `STOP` or `NOT RUN` means no production promotion.
