# Monitoring and alert contract

**Status:** infrastructure-neutral preparation. No provider dashboard, alert, or telemetry destination is configured by this change. Every threshold below is a **suggested initial threshold**, not a measured rotrack baseline.

## Ownership and identifiers

Populate the restricted operations inventory before staging verification:

| Responsibility | Placeholder |
|---|---|
| Service owner | `[service-owner]` |
| Frontend on-call | `[frontend-on-call]` |
| API on-call | `[api-on-call]` |
| Platform/ECS on-call | `[platform-on-call]` |
| Database on-call | `[database-on-call]` |
| Security on-call | `[security-on-call]` |
| Release owner | `[release-owner]` |
| Incident commander rotation | `[incident-commander-rotation]` |
| Communications lead rotation | `[communications-lead-rotation]` |

Alert identifiers use:

```text
rotrack.<environment>.<surface>.<signal>.<severity>
```

Allowed environments are `staging` and `production`; allowed severities are `warning` and `critical`. Examples: `rotrack.production.api.readiness.critical` and `rotrack.staging.frontend.exceptions.warning`. Provider names may be appended as tags, not substituted for the stable identifier. Dashboard identifiers use `rotrack.<environment>.<surface>.overview`.

Every alert includes environment, service/surface, stable alert ID, release ID, threshold/window, observed value, runbook link, and owner. Do not include request bodies, query strings, bearer/cookie values, credentials, auth payloads, note/reflection content, raw exception payloads, or unhashed user/resource IDs.

## Required signals and suggested initial alerts

These values are deliberately conservative starting points. Configure staging first, test routing, then copy the reviewed contract—not staging data or credentials—to production.

| Signal / stable alert suffix | Suggested initial condition | Window / evaluation | Initial owner | Release behavior |
|---|---|---|---|---|
| Public liveness `api.liveness.critical` | 2 consecutive synthetic `GET /api/v1/health` failures or non-200/contract mismatch | 1-minute probes; 2 of 2 | Platform/ECS | Stop rollout; page if serving production |
| Dependency readiness `api.readiness.warning` | Any desired task remains unready | 3 of 5 minutes | API + database | Stop rollout; investigate without restart churn |
| Ready capacity `ecs.ready-capacity.critical` | Ready tasks below configured minimum or zero ready targets | 2 consecutive minutes; immediate if zero | Platform/ECS | Roll back/incident |
| API latency `api.latency-p95.warning` | p95 > 750 ms, excluding health/readiness | 10 minutes and ≥100 requests | API | Stop rollout if release-correlated |
| API latency `api.latency-p95.critical` | p95 > 1,500 ms, excluding health/readiness | 5 minutes and ≥50 requests | API | Page / rollback decision |
| API errors `api.5xx-rate.warning` | 5xx ≥2% and ≥5 failures | 5 minutes | API | Stop rollout |
| API errors `api.5xx-rate.critical` | 5xx ≥5% and ≥10 failures, or any sustained 100% 5xx | 5 minutes; 2 minutes for 100% | API | Page / rollback decision |
| ECS unexpected restarts `ecs.restarts.warning` | >1 unexpected task/container restart for one service | 15 minutes | Platform/ECS | Stop rollout |
| ECS deployment/task health `ecs.deployment.critical` | Circuit breaker fires, task repeatedly exits, or deployment cannot reach steady state | immediate provider event / 10-minute deadline | Platform/ECS | Roll back / page |
| Auth failures `api.auth-failures.warning` | `401`/`403` count >3× same-window measured baseline and ≥50 | 10 minutes | API + security | Investigate issuer/JWKS/client release/attack |
| Auth failures `api.auth-failures.critical` | >5× measured baseline and ≥100, or valid-user canary auth fails | 5 minutes; canary 2 consecutive | Security + API | Page; stop rollout |
| Connection utilization `database.connections.warning` | Application pool/provider connections ≥70% of the approved app budget | 10 minutes | Database + platform | Stop scale-out/deploy |
| Connection saturation `database.connections.critical` | ≥85% of budget | 5 minutes | Database + platform | Page; stop deployment/replacement churn |
| Connection exhaustion `api.connection-acquire-timeout.critical` | Any database pool acquisition timeout or provider exhaustion error | ≥1 in 5 minutes | API + database | Page / remove unhealthy release |
| Migration status `database.migration-status.critical` | Failed/partial/unknown migration, checksum mismatch, or observed schema version differs from release expectation | immediate during release; 5-minute periodic check outside release | Migration operator + database | Block application deployment |
| Frontend exceptions `frontend.exceptions.warning` | Unhandled exception affects ≥1% of sessions and ≥10 events | 10 minutes | Frontend | Stop rollout if new release fingerprint |
| Frontend exceptions `frontend.exceptions.critical` | Unhandled exception affects ≥3% of sessions and ≥25 events, or authenticated route canary fails | 5 minutes; canary 2 consecutive | Frontend | Page / frontend rollback |

Low-traffic services can hide behind percentage minimums. Add an absolute critical condition of 10 API 5xx responses in 5 minutes and 25 frontend unhandled exceptions in 5 minutes even when a session/request denominator is unavailable. Conversely, one isolated user-caused 4xx is not an incident.

Readiness and liveness are intentionally distinct. A process can be live while PostgreSQL is unavailable; do not replace-loop every live task during a shared dependency outage. Alert on ready capacity and connection pressure together.

## Signal definitions

- **Route:** normalized Spring/Next route template such as `/api/v1/time-entries/{id}/stop`, never a raw URL or query string.
- **Latency:** server duration from request acceptance through response completion. Track p50/p95/p99 and request count by environment/service/route/status class; do not use high-cardinality IDs as dimensions.
- **Error rate:** API `5xx / all completed API requests`, excluding probes. Keep `401`, `403`, `404`, `409`, and validation `4xx` separate because they have different meanings.
- **Auth failures:** stable API security outcome (`401` vs `403` and safe error code), aggregated by environment/route/release. Never capture the token, claims/auth payload, email, IP in raw form, or response body.
- **Connections:** use the lower of the approved rotrack budget and provider limit as denominator. Include Hikari active/idle/pending/max, acquisition timeout count, maximum ECS tasks, and reserved migration/operations capacity.
- **Migration status:** release-expected migration name/checksum/version compared to observed history/catalog. Migration content, connection details, and SQL errors are restricted; dashboards show only sanitized status and identifiers.
- **Frontend exceptions:** unhandled error/rejection fingerprint, release, environment, route template, browser family, and scrubbed stack. Do not enable session replay, DOM capture, input capture, network-body capture, or authenticated headers.

## Required dashboards

Provision separate dashboards for each environment:

1. `rotrack.<env>.release.overview`: release annotations, live/ready state, request count, p95/p99 latency, 4xx classes, 5xx, frontend exceptions, current/desired/ready tasks, restarts, connections, migration status.
2. `rotrack.<env>.api.overview`: route/status-class/latency, security outcomes, pool active/pending/timeouts, JVM/process health, task health.
3. `rotrack.<env>.frontend.overview`: releases, page/route volume, unhandled error fingerprints, affected-session rate, authenticated route canary.
4. `rotrack.<env>.database.overview`: provider connection utilization, rotrack pool budget, acquisition timeouts, readiness, migration status. Do not expose database identifiers or query text outside the database operations group.

Dashboards must link to the [release runbook](../release/release-runbook.md) and [incident procedure](../incidents/incident-response.md) and show whether a threshold is `initial-unmeasured` or `measured-tuned`.

## Frontend/API monitoring boundary

- Use separate provider projects/accounts or strictly isolated projects for staging and production. DSNs, API keys, alert routes, dashboards, releases, and retention must not be shared across environments.
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
3. Measure per-route traffic, p95/p99, error/auth baseline, task churn, connection headroom, and frontend affected-session rate. Exclude load tests and known incidents from the normal baseline but retain their annotations.
4. Propose thresholds from service objectives and capacity limits (for example, baseline p99 plus justified headroom), not solely to suppress pages.
5. Review proposed changes with the signal owner and release/security owner; record old/new value, measured range, reason, and date.
6. Revisit monthly initially and after topology, pool, auth, or traffic changes.

Measured tuning may reduce noise but must not weaken immediate alerts for zero ready capacity, deployment failure, migration mismatch/failure, connection acquisition timeout, or authenticated canary failure without an explicit risk approval.
