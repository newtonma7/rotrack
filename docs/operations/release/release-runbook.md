# Release and rollback runbook

**Status — 2026-08-12:** source commit `744635c` passed focused Azure contract/readback, publish, preflight, RBAC, container, and release checks. The canonical hosted candidate passed ACA/Vercel readback, public smoke, hosted authenticated smoke (`4/4`, zero skipped/unexpected/flaky, API-target bound), and the corrected exact no-schema-change backend/frontend rollback rehearsal; final state is the candidate. Rate limiting remains explicitly deferred/accepted, and collector redaction, alert delivery/receipt, and alert routing evidence remain open. Ten genuine zero-replica trials completed on 2026-08-11 with readiness 10/10, p50 34.586 seconds, and p95/max 39.425 seconds; the 30-second p95 criterion was not met. The canonical shared-hosted app now uses `minReplicas=1` by product-owner decision; observe actual billing before revisiting. The backup limitation is accepted as already documented. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed. This runbook does not authorize production readiness or M3 completion.

The long-term separated topology protected by this runbook is database-first: Supabase Auth in the browser, one Vercel project (Preview for non-production, Production for production), Azure Container Apps Consumption for the Spring API, and two Supabase Free projects. In that long-term topology, approved authenticated E2E uses the non-production project and the separate production lane uses `rotrack-prod`; those are target/inventory labels, not the current canonical boundary. The current product-owner override uses the shared Supabase project, Vercel Production, and the existing ACA implementation boundary with the `production` runtime label. Credential-free PR CI uses isolated PostgreSQL. It preserves explicit timer sessions and the API ownership boundary. Application rollback must not silently rewrite active or completed sessions. Candidate evidence is recorded in [`../azure-nonproduction.md`](../azure-nonproduction.md) and [`../single-environment.md`](../single-environment.md); the M3/production-readiness STOP remains.

The repository now contains a separate, source-only production Azure lane in [`../../../deploy/azure/production/README.md`](../../../deploy/azure/production/README.md). It is not a production authorization or cloud-evidence claim. The lane requires an explicit production confirmation and selected production subscription identity, rejects the non-production boundary, and preserves immutable digest, secret reference, TLS CA, exact CORS, health/readiness, non-root, scaling, logging, and connection-budget checks.

## Roles and approvals

Replace these placeholders in the restricted release record before a staging rehearsal or production release:

| Role | Named person / rotation | Required action |
|---|---|---|
| Release owner | `[release-owner]` | Owns the checklist, immutable release record, and stop/go call |
| Migration operator | `[migration-operator]` | Reviews and applies ordered SQL; confirms observed schema version |
| Application operator | `[application-operator]` | Deploys/rolls back the immutable backend digest and environment-specific immutable Vercel deployments |
| Verification owner | `[verification-owner]` | Runs smoke checks and records sanitized results |
| Monitoring owner | `[monitoring-owner]` | Confirms dashboards, alerts, routing, and release annotations |
| Security approver | `[security-approver]` | Reviews trust-boundary, secret, auth, and privacy evidence |
| Incident commander | `[incident-commander]` | Takes control when a stop condition is met |
| Product approver | `[product-approver]` | Approves the tested backend digest and same-commit production frontend build |

No person may approve their own unreviewed migration or waive a failed release gate. Production requires explicit release-owner, migration-operator, monitoring-owner, security, and product approvals recorded with timestamps. A release stops when any required approver is unavailable.

## Immutable release record

Create one access-controlled record per release. Use a non-secret release ID such as `rotrack-YYYYMMDD-N`; do not put credentials, tokens, auth storage, private content, response bodies, or complete environment files in it.

Record:

- source commit and passing CI run;
- frontend immutable deployment identifier, source commit, and Vercel environment;
- backend OCI-compatible image registry digest and Container App revision;
- ordered migration filenames and reviewed checksums;
- current schema version and expected post-migration version;
- compatibility statement for **old app + new schema** and **new app + new schema**;
- long-term separated staging and production target/inventory references (when that topology is used);
- smoke and rollback-rehearsal evidence references;
- dashboard and alert identifiers from the monitoring contract;
- open defects, risk acceptance, approvals, start/end times, and outcome.

Never identify an artifact only by a mutable tag such as `latest`.

## Required rollout shape

Every database-dependent release uses **expand → migrate → application → contract**. The application deployment described here includes only expand/migrate-safe changes. A destructive contract migration is a later release after every supported application version has stopped reading and writing the old shape.

A migration is rollout-compatible only when all of the following are true:

1. The currently deployed application continues to start (`ddl-auto: validate`) and serve against the migrated schema.
2. Existing reads and writes remain valid while old and new revisions overlap.
3. New required columns have safe defaults or remain nullable until all writers populate them.
4. Renames use additive dual-read/dual-write transitions; tables, columns, indexes, enum values, constraints, and accepted values used by the old version are not removed in this release.
5. Backfills are bounded, restartable, observable, and do not hold unsafe locks for the full dataset.
6. The new application can be rolled back while leaving the migrated schema in place.

If compatibility cannot be proven, stop. Do not compensate with a simultaneous migration/application cutover.

## Preflight gate

For the long-term separated topology, the release owner verifies separate non-production and production inventories: GitHub `nonproduction` and its ACA boundary map to runtime/telemetry label `staging`, while GitHub `production` maps to `production`. This mapping is historical/target guidance for the reserved separated topology. Under the current shared-hosted-production override, the canonical shared Supabase project, Vercel Production, and existing ACA implementation boundary use the `production` runtime label:

- CI passed on the exact commit; there are no open critical/high security or data-integrity defects.
- The canonical hosted candidate's public smoke, hosted authenticated smoke, and corrected exact no-schema-change backend/frontend rollback rehearsal passed for the reviewed source commit. Full artifact identifiers remain in private evidence.
- Frontend/API origins, Supabase project, database host/project reference, runtime role, and monitoring environment all resolve to the intended environment. Do not print their secret values.
- Supabase Free pause-warning/resume ownership is assigned for both projects. Because Free projects may auto-pause after seven days of low activity, the release record includes the expected recovery owner and test path; do not claim this control is configured without evidence.
- The Free topology has no automatic daily backups or PITR. Before production promotion, an encrypted, access-controlled off-site `supabase db dump` export exists with approved retention and a successful restore rehearsal, or the product owner has recorded explicit data-loss risk acceptance. See the official [Free project pausing](https://supabase.com/docs/guides/platform/free-project-pausing), [database backups](https://supabase.com/docs/guides/platform/backups), and [CLI dump](https://supabase.com/docs/reference/cli/supabase-db-dump) documentation.
- Database capacity, migration connection reservation, and current connection utilization are acceptable.
- The previous compatible frontend/backend release is still available by immutable identifier.
- Container App managed-environment/revision/traffic controls, bounded connection pool, liveness, readiness, exact CORS, TLS CA injection, and secret references are configured and reviewed; the managed environment and app are inside the matching target resource group and match the logical GitHub environment.
- Azure budget alerts and credit-expiry notifications are reviewed as notifications only, not a hard spending cap; delayed cost/credit data is accounted for in the stop/go decision.
- At least 10 non-production scale-from-zero trials are recorded. The 2026-08-11 run recorded readiness 10/10, p50 34.586 seconds, and p95/max 39.425 seconds; because p95 exceeded 30 seconds, the scale-to-zero criterion was not met. The canonical shared-hosted path now uses minimum replicas `1` by product-owner decision; revisit after actual billing is observed.
- Migration locks/runtime/backfill behavior and checksums were reviewed; a forward-fix owner and decision deadline are named.
- Incident roles, communication channel, and provider access are staffed for the rollout and observation window.
- The monitoring owner has tested alert routing in the target environment without generating a production incident, including Free-project pause/resume, logical-export freshness/restore readiness, cold starts, and Azure budget/credit-expiry notifications.
- Approved edge/backend rate limits protect authentication-adjacent and mutation endpoints. Staging evidence covers expected `429` responses, trusted client-identity handling, recovery after the window, and proxy/backend failure behavior without bypass or fail-open exposure. A process-local authenticated mutation limiter is implemented as defense in depth; the trusted fleet-wide/authentication-adjacent edge control and staging failure-mode evidence remain incomplete, so this item is currently **STOP**.

Any mismatch, unavailable rollback artifact, unknown migration state, failed readiness, missing required-auth test configuration, or missing approval is **STOP**.

## Ordered rollout

### 1. Freeze and annotate

1. Announce the release window and freeze unrelated deployments/migrations.
2. Record baseline frontend exceptions, API latency/error rate, auth failures, ready replica count/restarts, and database connections.
3. Attach the release ID to deployment and monitoring annotations.

### 2. Apply and verify the database migration first

1. Migration operator rechecks the target identity and observed schema version against the release record.
2. Confirm the old application is compatible with the proposed schema and that rollback leaves the migration applied.
3. Apply only the reviewed ordered migrations using the approved provider path and a separately authorized migration identity. Never use the runtime application's DML role for schema changes.
4. Record only migration name/checksum, timestamps, status, and sanitized error code. Never record connection strings or SQL containing private values.
5. Verify the expected schema version, required constraints/indexes/policies/grants, and signup/runtime ownership boundaries using the approved migration checks.
6. Confirm the prior revision's replicas remain live and ready and that connection/latency/error alerts remain clear during the observation window.

A failed, partially applied, unexpected, or long-blocking migration is **STOP**. Do not deploy the application. Prefer a reviewed forward fix; see migration rollback limits below.

### 3. Deploy the backend application

1. Deploy the exact backend OCI-compatible image registry digest to the matching Azure Container App through the logical GitHub `nonproduction` or `production` environment. If ACR is selected, pull it through managed identity.
2. Do not route a revision until `GET /api/v1/readiness` returns `200 {"status":"ready"}`. Liveness is independently `GET /api/v1/health` → `200 {"status":"ok"}`.
3. Keep a compatible prior revision available until the new revision is ready. Watch readiness, 5xx, latency, auth failures, replica/restart health, cold starts, and database connection utilization.
4. Verify the running image digest and Container App revision/traffic state match the release record.
5. Treat an initial scale-from-zero delay as a cold-start observation, not automatic outage evidence; repeated timeout beyond the approved grace is STOP.

Readiness 503, unexpected restart, digest mismatch, ownership/auth regression, elevated 5xx, or connection pressure is **STOP** and triggers application rollback.

### 4. Deploy the frontend application

1. For non-production, deploy the reviewed source commit to Vercel Preview with non-production `NEXT_PUBLIC_*` values. For production, build that same reviewed commit in Vercel Production with production-scoped values; do not promote Preview bytes because those public values are embedded at build time.
2. Verify its public API URL and Supabase Auth project belong to the same intended environment and CORS allows only the exact frontend origin.
3. Verify the immutable deployment identifier, source commit, environment scope, and release annotation.
4. Run the approved production-safe, non-mutating frontend verification. Any source-commit mismatch or unexpected endpoint is a new candidate and **STOP**.

### 5. Smoke and observe

For the long-term separated topology, the historical procedure is:

1. Run `scripts/release/staging-smoke.sh` in the separated non-production environment before production approval. For the reserved separated production lane, use an independently approved non-mutating production smoke plan; the tracked authenticated script is scoped to the historical staging boundary and must not be used as the current canonical production plan.
2. The historical staging script verifies frontend availability, independent liveness/readiness, and runs Playwright with `ROTRACK_E2E_REQUIRE_AUTH=1`. All four Chromium tests must pass with zero skips.
3. Observe the initial windows in the [monitoring contract](../monitoring/monitoring-contract.md); compare against the recorded baseline.
4. Verification owner and monitoring owner attach sanitized results to the immutable release record.

The current canonical rehearsal instead targets the shared Supabase project, Vercel Production, and existing ACA implementation boundary with the `production` runtime label. Its 2026-08-11 public smoke and hosted authenticated `4/4` result passed with API-target binding. The authenticated suite uses disposable synthetic data only; retained operator-owned accounts and stopped rows are not claimed as cleaned up.

## Application rollback

Rollback the application when a stop condition follows a successful compatible migration:

1. Incident commander freezes further deployment and names the rollback decision time.
2. Application operator restores the prior **backend image registry digest/Container App revision** and waits for the prior revision to become ready before removing the failed revision.
3. Restore the prior **frontend immutable deployment** that targets the same API/schema. If only one tier regressed, the release compatibility matrix must explicitly permit a partial rollback; otherwise restore both as one release.
4. Leave the backward-compatible database migration applied.
5. Run public liveness/readiness and the approved smoke boundary. Confirm revision/replica health, errors, latency, auth failures, frontend exceptions, and connections return to baseline.
6. Keep the release blocked, preserve sanitized evidence, and open an incident/problem record. Redeployment is a new stop/go decision.

Do not change timer/session rows during rollback and do not stop users' active sessions. Do not route traffic to unready revisions or replicas.

## Migration rollback limits

The ordered SQL migrations do not promise reversible `down` scripts. Application rollback and migration rollback are different operations.

- Additive schema changes normally remain after application rollback.
- Dropped/renamed data, lossy type conversions, enum removal, rewritten timestamps, and destructive backfills cannot be made safe by redeploying an old application.
- Transactional DDL may roll back only if the migration tool reports the transaction failed before commit. Do not assume all provider operations or concurrent index operations share that behavior.
- A manual reverse migration is allowed only after independent review proves it preserves data and remains compatible with the running application. It is applied as a new ordered forward migration, not by editing migration history.
- Point-in-time restore is disaster recovery, not routine rollback: it creates an outage/cutover problem and can discard writes after the restore point. It requires incident-command, database-owner, security, and product approval plus a reconciliation plan.
- If a migration is committed and breaks both old and new application versions, stop traffic-changing work and choose a reviewed forward fix or disaster recovery. Never improvise destructive SQL during the incident.

## Rehearsal boundaries

For the current single-environment decision, keep the target inventory outside the repository and export assignments without printing values. The current smoke target is the canonical ACA implementation boundary, canonical Vercel Production alias, shared hosted Supabase project, and approved API base. Playwright rejects frontend responses from any other API and the authenticated result accepts exactly four passed tests with zero skipped, unexpected, or flaky results. The 2026-08-11 hosted run passed `4/4` with API-target binding; retained synthetic accounts and stopped rows remain by product-owner decision, so cleanup is not claimed. Full hosts, auth-state paths, and artifact identifiers remain private.

The following hook procedure is retained for the long-term separated topology; it is not a claim that the current canonical rehearsal was staging-only. The long-term separated smoke target is the non-production environment, its Vercel Preview deployment, and its separate ACA boundary.

The rollback rehearsal expects the candidate to be currently deployed. Operator-owned hooks have this interface (the existing scripts still contain historical AWS-era assumptions and are not Azure verification):

```text
inspect hook:  INSPECT_HOOK <nonproduction-target-id> -> exact current release ID on stdout
deploy hook:   DEPLOY_HOOK <nonproduction-target-id> <prior-release-id>
```

A release ID maps to the immutable backend digest and the environment-specific immutable Vercel deployment in the restricted release inventory. Hooks must validate non-production again, use approved provider authentication, emit no secrets, and return nonzero on any partial or uncertain result. The rehearsal:

1. verifies the candidate release ID;
2. runs baseline staging smoke;
3. restores the prior application release only;
4. waits until the prior release ID is observed;
5. reruns staging smoke; and
6. leaves staging on the prior release for an explicit subsequent rollout.

It never applies or reverses a migration. The corrected 2026-08-11 rehearsal passed prior backend health, prior frontend promotion, rollback public smoke, rollback authenticated `4/4`, candidate restoration, and final candidate health/CORS/auth; the final state is the candidate. Keep rate limiting, collector redaction, alert delivery/receipt, and alert routing evidence open. Ten cold-start trials are now recorded, but p95 readiness was 39.425 seconds, above the 30-second criterion; the canonical shared-hosted app now uses `minReplicas=1` by product-owner decision.

## Product-owner pre-user posture — 2026-08-11

The product has zero active users. Broad per-signal alert coverage, threshold tuning/observation windows, dashboard and retention/access expansion, collector-side second-layer redaction proof, and fleet-wide edge rate limiting are deferred until before real-user onboarding or until usage/abuse/telemetry risk makes them material. This is an explicit risk acceptance; it does not turn the M3 release gate into Verified.

## Stop/go record

Record each gate as `GO`, `STOP`, or `NOT RUN`, with owner, UTC timestamp, and evidence link. The following is the sanitized 2026-08-11 reconciliation; full identifiers remain in private evidence:

| Gate | Current result |
|---|---|
| Exact CI artifact and migration checksums | GO |
| Backward compatibility / application rollback matrix | GO |
| Separate target and no-secret configuration review | GO |
| Migration apply and schema verification | GO |
| Backend readiness / rolling deployment | GO |
| Same-commit environment-specific frontend build / exact CORS | GO |
| Required-auth hosted smoke (4 passed, 0 skipped/unexpected/flaky) | GO |
| Corrected exact no-schema-change rollback rehearsal | GO |
| Collector redaction, alerts, routes, and incident staffing | STOP |
| Free pause-warning/resume ownership | NOT RUN |
| Encrypted logical export retention and restore rehearsal, or accepted documented limitation | GO (accepted limitation) |
| Azure budget/credit-expiry notifications reviewed as delayed, non-cap signals | NOT RUN |
| Rate limits and `429`/bypass/failure tests | STOP (deferred/accepted) |
| Cold-start trials and observation window | NOT MET — 10 trials recorded; p95 39.425s exceeded 30s; canonical shared-hosted path uses min replicas `1` |

One `STOP` or `NOT RUN` means no production promotion or M3 completion.
