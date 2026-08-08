# Incident response and release-stop procedure

**Status:** role and procedure template. Replace placeholders, test routing in staging, and obtain organizational approval before production promotion.

## Roles

| Role | Placeholder | Responsibility |
|---|---|---|
| Incident commander (IC) | `[incident-commander]` | Declares severity, owns decisions/timeline, assigns roles |
| Operations lead | `[operations-lead]` | ECS/Vercel/Supabase diagnosis and mitigations |
| Application lead | `[application-lead]` | Frontend/API diagnosis, rollback compatibility |
| Database lead | `[database-lead]` | Connections, migrations, recovery decisions |
| Security/privacy lead | `[security-privacy-lead]` | Auth, suspected exposure, evidence handling |
| Communications lead | `[communications-lead]` | Internal/status/customer communications |
| Scribe | `[scribe]` | Sanitized UTC timeline, decisions, evidence references |
| Product owner | `[product-owner]` | User impact and recovery tradeoffs |

The active IC must be one named person, not a team alias. Responders acknowledge in the restricted incident channel. Provider escalation contacts and access procedures live in the restricted operations inventory, never this public repository.

## Severity and activation

- **SEV-1:** confirmed/likely credential or private-data exposure; cross-user access; data corruption/loss; all production traffic unavailable; unsafe migration state. Page IC, security/privacy, application, database, and operations immediately.
- **SEV-2:** material authenticated flow failure, sustained zero/insufficient ready capacity, critical error/latency/connection alert, deployment rollback required, or large frontend failure. Page IC and owning leads.
- **SEV-3:** degraded non-critical behavior, warning threshold, or staging-only release failure without production impact. Ticket/working channel; staging remains blocked.

Any cross-user ownership anomaly, unexpected timer/session mutation, migration mismatch, or possible secret/private-content telemetry is SEV-1 until disproven.

## First 15 minutes

1. Alert receiver acknowledges and opens the restricted incident channel/record with UTC time, stable alert ID, environment, release ID, and symptom.
2. IC declares severity and assigns every required role. Staging/production target is stated explicitly.
3. Freeze deployments, migrations, scaling changes, and unrelated configuration. A release incident is an immediate `STOP` in the release record.
4. Preserve safe release/deployment identifiers, alert transitions, aggregate metrics, and correlation IDs. Do not copy raw requests, bodies, tokens, cookies, browser storage, database strings, notes/reflections, auth payloads, or complete logs into chat/tickets.
5. Establish user impact and trust-boundary impact before optimizing recovery speed.
6. Choose the smallest reversible mitigation: remove an unready task, stop a rollout, restore the prior compatible application release, reduce replacement churn, or isolate telemetry ingestion. Do not improvise a database reverse migration.
7. Communications lead sets the next-update time even if diagnosis is incomplete.

## Scenario guides

### Liveness / ready-capacity failure

- Compare public liveness with dependency readiness and ECS desired/running/ready counts.
- If liveness fails, inspect task exit/deployment/provider events and restore the prior immutable application release when release-correlated.
- If liveness is 200 but readiness is 503 across tasks, treat PostgreSQL/connection state as a shared dependency issue. Stop deployment/replacement churn; repeatedly replacing live tasks can worsen connection exhaustion.
- Never route traffic to unready tasks or weaken readiness to make a deployment appear healthy.

### Latency / API 5xx / frontend exceptions

- Slice by environment, release, normalized route, status class, and safe fingerprint; do not pivot on raw user/resource identifiers.
- Compare candidate vs prior release and baseline. If release-correlated and the migrated schema remains old-app compatible, use application rollback.
- Verify both frontend and backend immutable identifiers after rollback, then observe the full critical alert window.
- Do not capture authenticated network traces or session replay as a quick diagnostic.

### Database connection saturation or exhaustion

- Stop rollout and automatic scale-out/replacement that would open more pools.
- Compare `maximum pool size × maximum tasks` with the approved app budget and provider connection limit; preserve migration/operations reserve.
- Inspect active/pending/acquisition-timeout aggregates and provider state without exporting query text, connection strings, or credentials.
- Restore the prior app if the new release increased connection demand. Do not raise pool limits without database-lead capacity approval.

### Migration failed, partial, or mismatched

- Do not deploy the new application and do not edit migration history.
- Record migration name/checksum, expected/observed version, transaction status, and sanitized provider error category.
- Keep the old app serving only if compatibility and data integrity are proven.
- Prefer an independently reviewed forward-fix migration. Manual reverse SQL requires the approvals and proof in the release runbook.
- Point-in-time restore is a SEV-1 disaster-recovery decision because post-restore writes may be lost or require reconciliation.

### Authentication failure spike

- Separate `401` from `403`, route, release, and environment. Check the valid-user canary, issuer/JWKS reachability/configuration, clock, CORS, and frontend environment mapping.
- Never decode, paste, or attach a real JWT. Use synthetic/generated tokens only in controlled tests.
- If credential exposure is suspected, engage security/privacy lead, isolate the source, revoke/rotate through the provider procedure, and avoid announcing sensitive rotation details in broad channels.
- A cross-user access or ownership `404` regression is SEV-1; block traffic to the affected release and preserve only sanitized correlation evidence.

### Sensitive telemetry or frontend artifact exposure

- Stop/disable the affected export, replay, trace, screenshot, or logging sink while preserving access-controlled provider audit evidence.
- Restrict access; do not duplicate the sensitive artifact into a ticket.
- Security/privacy lead determines exposure scope, deletion/retention/legal obligations, and credential rotation.
- Validate the two-layer drop rules with synthetic sentinels before re-enabling ingestion.

## Rollback decision

Application rollback is preferred when impact is release-correlated and the new schema is explicitly compatible with the prior app. Follow the [release runbook](../release/release-runbook.md) and restore immutable frontend/backend artifacts. Leave compatible additive migrations applied.

If rollback compatibility is unknown, do not guess. IC, application lead, and database lead choose between traffic containment, reviewed forward fix, and disaster recovery. Active user sessions must not be mass-stopped or rewritten as a mitigation.

Recovery is not declared until:

- exact intended artifacts and schema status are known;
- live/ready capacity is stable;
- critical latency/error/auth/connection/frontend windows are clear;
- authenticated ownership/timer behavior is verified in the authorized environment;
- monitoring and alert routing are functioning; and
- IC, owning leads, and product owner agree impact has ended.

## Communications

Every update contains UTC timestamp, severity/environment, confirmed user impact, current mitigation, remaining risk, and next update time. State unknowns plainly. Never include email, user/resource IDs, credentials, tokens, cookies, notes/reflections, auth payloads, response bodies, query strings, database identifiers, or private provider screenshots.

Only the communications lead publishes external updates after IC and security/privacy review. Avoid claiming “no data exposure” until the security/privacy lead has evidence.

## Evidence and follow-up

The incident record contains sanitized:

- stable alert IDs and aggregate observations;
- release, image digest/task-definition, frontend deployment, and migration identifiers;
- correlation IDs and normalized routes (no raw URLs/query);
- UTC timeline, decisions/approvers, mitigations, and verification results;
- provider evidence links with access restrictions, not copied raw artifacts;
- notification/rotation actions and known evidence gaps.

After stabilization, rotate any exposed credentials, remove temporary access, verify telemetry deletion/retention actions, and open corrective work. Hold a blameless review for SEV-1/2 with detection, response, rollback, privacy, and recurrence actions, each with owner and due date. A failed staging rehearsal produces a problem record and blocks production even when it caused no production incident.
