# Monitoring and alert contract

**Status — 2026-08-12:** the candidate passed public smoke, hosted authenticated smoke (`4/4`, zero skipped/unexpected/flaky, API-target bound), and corrected exact no-schema-change backend/frontend rollback rehearsal. The application logging boundary and ACA runtime label are read back, and aggregate Log Analytics ingestion is now observed, but collector redaction, broad per-signal delivery, and alert routing remain open; one alternate temporary metric-alert path has a confirmed receipt, while provider-synthetic delivery remains unverified. Rate limiting is explicitly deferred/accepted; Cloudflare Free is future exploration only. Ten genuine zero-replica trials completed on 2026-08-11 with readiness 10/10, p50 34.586 seconds, and p95/max 39.425 seconds; the 30-second p95 criterion was not met. The canonical shared-hosted app now uses `minReplicas=1` by product-owner decision; observe actual billing before revisiting. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed. The backup limitation is accepted as already documented. Every threshold below is a **suggested initial threshold**, not a measured rotrack baseline. The canonical shared-hosted path currently uses `minReplicas=1`; scale-to-zero remains relevant only to a future/reserved topology decision, while credit-expiry risk still requires explicit signals.

## Product-owner pre-user scope — 2026-08-11

The product has zero active users. Broad per-signal alert coverage, threshold tuning/observation windows, dashboard and retention/access expansion, and collector-side second-layer redaction proof are deferred until before real-user onboarding or until usage/telemetry risk makes them material. The minimum posture remains liveness/readiness, one verified alert path, and sanitized application logging. This is an explicit pre-user risk acceptance; deferred controls are not verified.

## Alert-route probe — 2026-08-11

The Azure Free-subscription test-notification API rejected both budget and service-health synthetic tests. A temporary one-minute `Requests` metric alert was therefore created safely, triggered by one unauthenticated health request, observed in `Fired` state, and deleted successfully. The existing action group was attached and no receivers changed; the product owner confirmed receipt of the harmless notification. This verifies one end-to-end alert delivery path; broader per-signal alert coverage and observation remain open.

## Ownership and identifiers

Populate the restricted operations inventory before staging verification:

| Responsibility | Placeholder |
|---|---|
| Service owner | `[service-owner]` |
| Frontend on-call | `[frontend-on-call]` |
| API on-call | `[api-on-call]` |
| Platform/Container Apps on-call | `[platform-on-call]` |
| Database on-call | `[database-on-call]` |
| Security on-call | `[security-on-call]` |
| Release owner | `[release-owner]` |
| Incident commander rotation | `[incident-commander-rotation]` |
| Communications lead rotation | `[communications-lead-rotation]` |

Alert identifiers use:

```text
rotrack.<environment>.<surface>.<signal>.<severity>
```

Allowed telemetry environments are `staging` and `production`; allowed severities are `warning` and `critical`. Canonical mapping: GitHub/ACA `nonproduction` → runtime/telemetry `staging`, and GitHub/ACA `production` → runtime/telemetry `production`. Examples: `rotrack.production.api.readiness.critical` and `rotrack.staging.frontend.exceptions.warning`. Provider names may be appended as tags, not substituted for the stable identifier. Dashboard identifiers use `rotrack.<environment>.<surface>.overview`.

Every alert includes environment, service/surface, stable alert ID, release ID, threshold/window, observed value, runbook link, and owner. Do not include request bodies, query strings, bearer/cookie values, credentials, auth payloads, note/reflection content, raw exception payloads, or unhashed user/resource IDs.

## Required signals and suggested initial alerts

These values are deliberately conservative starting points. Configure non-production first, test routing, then copy the reviewed contract—not non-production data or credentials—to production.

| Signal / stable alert suffix | Suggested initial condition | Window / evaluation | Initial owner | Release behavior |
|---|---|---|---|---|
| Public liveness `api.liveness.critical` | 2 consecutive synthetic `GET /api/v1/health` failures or non-200/contract mismatch | 1-minute probes; 2 of 2 | Platform/Container Apps | Stop rollout; page if serving production |
| Dependency readiness `api.readiness.warning` | Any desired replica remains unready beyond the approved cold-start grace | 3 of 5 minutes | API + database | Stop rollout; investigate without restart churn |
| Ready capacity `container-apps.ready-capacity.critical` | Ready replicas below configured minimum or zero ready targets | 2 consecutive minutes; immediate if zero | Platform/Container Apps | Roll back/incident |
| API latency `api.latency-p95.warning` | p95 > 750 ms, excluding health/readiness | 10 minutes and ≥100 requests | API | Stop rollout if release-correlated |
| API latency `api.latency-p95.critical` | p95 > 1,500 ms, excluding health/readiness | 5 minutes and ≥50 requests | API | Page / rollback decision |
| API errors `api.5xx-rate.warning` | 5xx ≥2% and ≥5 failures | 5 minutes | API | Stop rollout |
| API errors `api.5xx-rate.critical` | 5xx ≥5% and ≥10 failures, or any sustained 100% 5xx | 5 minutes; 2 minutes for 100% | API | Page / rollback decision |
| Container App unexpected restarts `container-apps.restarts.warning` | >1 unexpected replica/container restart for one app | 15 minutes | Platform/Container Apps | Stop rollout |
| Container App deployment health `container-apps.deployment.critical` | Revision repeatedly exits, traffic cannot reach ready state, or deployment cannot stabilize | immediate provider event / 10-minute deadline | Platform/Container Apps | Roll back / page |
| Auth failures `api.auth-failures.warning` | `401`/`403` count >3× same-window measured baseline and ≥50 | 10 minutes | API + security | Investigate issuer/JWKS/client release/attack |
| Auth failures `api.auth-failures.critical` | >5× measured baseline and ≥100, or valid-user canary auth fails | 5 minutes; canary 2 consecutive | Security + API | Page; stop rollout |
| Connection utilization `database.connections.warning` | Application pool/provider connections ≥70% of the approved app budget | 10 minutes | Database + platform | Stop scale-out/deploy |
| Connection saturation `database.connections.critical` | ≥85% of budget | 5 minutes | Database + platform | Page; stop deployment/replacement churn |
| Connection exhaustion `api.connection-acquire-timeout.critical` | Any database pool acquisition timeout or provider exhaustion error | ≥1 in 5 minutes | API + database | Page / remove unhealthy release |
| Migration status `database.migration-status.critical` | Failed/partial/unknown migration, checksum mismatch, or observed schema version differs from release expectation | immediate during release; 5-minute periodic check outside release | Migration operator + database | Block application deployment |
| Frontend exceptions `frontend.exceptions.warning` | Unhandled exception affects ≥1% of sessions and ≥10 events | 10 minutes | Frontend | Stop rollout if new release fingerprint |
| Frontend exceptions `frontend.exceptions.critical` | Unhandled exception affects ≥3% of sessions and ≥25 events, or authenticated route canary fails | 5 minutes; canary 2 consecutive | Frontend | Page / frontend rollback |
| Cold start `container-apps.cold-start.warning` | Initial scale-from-zero readiness exceeds 30 seconds, or any trial exceeds 60 seconds | 2 consecutive wake-ups; measured run p95 was 39.425 seconds | Platform/Container Apps | Scale-to-zero criterion is not met; set minimum replicas to `1` if the accepted risk is no longer appropriate |
| Azure budget/credit `azure.budget.critical` | Budget notification threshold, forecast breach, or credit-expiry window is reached | provider budget cadence / immediate expiry threshold | Platform/finance | Stop scale-out and production promotion; page owner. Notifications are not a hard spending cap and cost/credit data can be delayed |
| Supabase pause `supabase.free-pause.warning` | Low-activity warning, unexpected paused state, or resume failure for either Free project | provider warning cadence / immediate paused state | Database/platform | Notify resume owner; stop production promotion until access and recovery are understood |
| Logical backup `database.logical-backup.warning` | Expected encrypted off-site `supabase db dump` export is missing, stale, inaccessible, or restore rehearsal is overdue | daily freshness / release gate | Database/release | Stop production promotion or invoke explicit product-owner data-loss risk acceptance |

Free-plan pause, export, and restore signals are preparation requirements, not evidence that controls are configured. Low-traffic services can hide behind percentage minimums. Add an absolute critical condition of 10 API 5xx responses in 5 minutes and 25 frontend unhandled exceptions in 5 minutes even when a session/request denominator is unavailable. Conversely, one isolated user-caused 4xx is not an incident.

Readiness and liveness are intentionally distinct. A process can be live while PostgreSQL is unavailable; do not replace-loop every live replica during a shared dependency outage. Alert on ready capacity, cold-start behavior, and connection pressure together.

## Signal definitions

- **Route:** normalized Spring/Next route template such as `/api/v1/time-entries/{id}/stop`, never a raw URL or query string.
- **Latency:** server duration from request acceptance through response completion. Track p50/p95/p99 and request count by environment/service/route/status class; do not use high-cardinality IDs as dimensions.
- **Error rate:** API `5xx / all completed API requests`, excluding probes. Keep `401`, `403`, `404`, `409`, and validation `4xx` separate because they have different meanings.
- **Auth failures:** stable API security outcome (`401` vs `403` and safe error code), aggregated by environment/route/release. Never capture the token, claims/auth payload, email, IP in raw form, or response body.
- **Connections:** use the lower of the approved rotrack budget and provider limit as denominator. Include Hikari active/idle/pending/max, acquisition timeout count, maximum Container App replicas, revision overlap, and reserved migration/operations capacity.
- **Supabase Free operations:** track pause warning/state, resume outcome, logical-export freshness/access, retention, and restore-rehearsal status without exposing project identifiers or backup contents. Follow the official [pausing](https://supabase.com/docs/guides/platform/free-project-pausing), [production checklist](https://supabase.com/docs/guides/deployment/going-into-prod), and [`supabase db dump`](https://supabase.com/docs/reference/cli/supabase-db-dump) guidance.
- **Migration status:** release-expected migration name/checksum/version compared to observed history/catalog. Migration content, connection details, and SQL errors are restricted; dashboards show only sanitized status and identifiers.
- **Frontend exceptions:** unhandled error/rejection fingerprint, release, environment, route template, browser family, and scrubbed stack. Do not enable session replay, DOM capture, input capture, network-body capture, or authenticated headers.

## Required dashboards

Provision separate dashboards for each environment:

1. `rotrack.<env>.release.overview`: release annotations, live/ready state, request count, p95/p99 latency, 4xx classes, 5xx, frontend exceptions, current/desired/ready replicas, restarts, cold starts, connections, migration status, budget/credit risk, pause state, and logical-backup readiness.
2. `rotrack.<env>.api.overview`: route/status-class/latency, security outcomes, pool active/pending/timeouts, JVM/process health, Container App revision health.
3. `rotrack.<env>.frontend.overview`: releases, page/route volume, unhandled error fingerprints, affected-session rate, authenticated route canary.
4. `rotrack.<env>.database.overview`: provider connection utilization, rotrack pool budget, acquisition timeouts, readiness, migration status. Do not expose database identifiers or query text outside the database operations group.

Dashboards must link to the [release runbook](../release/release-runbook.md) and [incident procedure](../incidents/incident-response.md) and show whether a threshold is `initial-unmeasured` or `measured-tuned`.

## Frontend/API monitoring boundary

- Use the existing shared non-production Supabase project for development and approved environment-scoped authenticated E2E; credential-free PR CI uses isolated disposable PostgreSQL and does not touch hosted Supabase. Use the separate `rotrack-prod` project for production. Keep DSNs, API keys, alert routes, dashboards, releases, and retention logically separated; production must never use non-production credentials.
- Keep separate Azure managed environments (`rotrack-nonproduction-env` and `rotrack-production-env`), each inside its separate resource group and with a separate Container App, plus target logical GitHub `nonproduction`/`production` environments. Use one Vercel project with Preview and Production rather than a dedicated staging project.
- Browser monitoring may use only a provider's public ingestion key. Administrative/provider tokens remain server-side secrets. Never place a service-role or database credential in `NEXT_PUBLIC_*` configuration.
- Tag both frontend and API telemetry with the same non-secret release ID and environment. A correlation ID may join browser error and API request records only if the API returns a validated safe ID; until that exists, correlate by release/time/route without inventing a user identifier.
- API monitoring consumes metrics and scrubbed logs described in the [structured logging contract](structured-logging.md). It must not ingest request/response bodies or raw authentication failures.
- Frontend monitoring records scrubbed stack/fingerprint, route template, release, environment, and coarse browser/runtime data. Strip query strings, URL fragments, form/input values, local/session storage, cookies, request headers/bodies, Supabase SDK payloads, email, note text, reflection text, and time-entry notes before transport.
- Disable session replay, DOM snapshots, console breadcrumbs containing application data, network payload capture, and automatic user identification by default. Enabling any of these requires a separate privacy/security architecture review.
- Configure source maps as restricted release artifacts; they must not contain environment files or secrets. Provider access is least privilege and audited.

## Retention and access

Suggested initial maximum retention (shorten when the provider permits):

| Data | Staging | Production | Access |
|---|---:|---:|---|
| Metrics and aggregates | 14 days | 90 days | Service/platform/database on-call as relevant |
| Scrubbed application logs | 7 days | 30 days | API/platform on-call; security by incident need |
| Scrubbed frontend error events | 7 days | 30 days | Frontend on-call; security by incident need |
| Alert state and sanitized release/incident evidence | 90 days | 1 year | Release/incident/security owners |
| Raw authenticated browser artifacts | Disabled; delete immediately after checked run | Prohibited | Staging verification owner only when generated |

Access uses SSO/MFA, role-based least privilege, and provider audit logs. Review membership quarterly and after role changes. Exporting events to tickets/chat must preserve the same redaction policy. Legal/security requirements may mandate shorter or longer retention; record an approved exception rather than silently changing it.

## Tuning from measured data

1. Mark all initial alerts `initial-unmeasured`.
2. Collect at least two representative weeks including one controlled staging deployment; production critical release gates remain active during this period.
3. Measure per-route traffic, p95/p99, error/auth baseline, replica/revision churn, cold-start latency, connection headroom, budget/credit headroom, pause/resume behavior, logical-export freshness/restore readiness, and frontend affected-session rate. Exclude load tests and known incidents from the normal baseline but retain their annotations.
4. Propose thresholds from service objectives and capacity limits (for example, baseline p99 plus justified headroom), not solely to suppress pages.
5. Review proposed changes with the signal owner and release/security owner; record old/new value, measured range, reason, and date.
6. Revisit monthly initially and after topology, pool, auth, or traffic changes.

Measured tuning may reduce noise but must not weaken immediate alerts for zero ready capacity, deployment failure, migration mismatch/failure, connection acquisition timeout, or authenticated canary failure without an explicit risk approval.
