# Non-production deployment, smoke, and teardown checklist

**Current status:** preparation only. No remote Azure, Vercel, GitHub, Supabase, ACR, domain, alert, or billing/credit control is claimed as configured or verified. This checklist targets the approved non-production boundary: the existing shared non-production/dev Supabase Free project, Vercel Preview in the one Vercel project, logical GitHub `nonproduction`, resource group `rotrack-nonproduction`, and Container App `rotrack-api-nonproduction`.

Production is a separate release target: `rotrack-prod`, Vercel Production in the same Vercel project, logical GitHub `production`, resource group `rotrack-production`, and Container App `rotrack-api-production`. Never substitute one boundary for another.

Use [`deploy/staging/README.md`](../../../deploy/staging/README.md) for the target contract and [`evidence-template.md`](evidence-template.md) for redacted results. The checked-in AWS/ECS scripts and templates under `deploy/staging/` are historical/unselected and intentionally do not satisfy this checklist.

## 1. Authorization and identity gate

- [ ] Record change owner, non-production authorization, window, teardown owner, and approval outside Git.
- [ ] Select exactly the existing shared non-production Supabase project. Do not create or select a third project.
- [ ] Select Vercel Preview in the one approved Vercel project; do not select a dedicated staging project.
- [ ] Confirm the logical GitHub environment is `nonproduction` and its protected variables/secrets are scoped to non-production. Read back repository visibility/plan and required-reviewer/branch/environment-secret support; if required controls are unavailable, production remains stopped until the plan changes or an independently reviewed equivalent gate is approved.
- [ ] Confirm Azure subscription, managed environment `rotrack-nonproduction-env`, resource group `rotrack-nonproduction`, and Container App `rotrack-api-nonproduction` from authoritative readback. The managed environment is the Azure security boundary and must not be shared with production. Do not record identifiers in evidence.
- [ ] Confirm every selected identity is non-production and no production user, secret, API URL, database host, or browser state is selected.
- [ ] Confirm disposable non-production users/data and a teardown date.

Stop immediately on an identity mismatch. Never change the expected production or non-production identity to make a check pass.

## 2. Supabase database and Auth

### CA, TLS, and migrations

- [ ] Obtain the official CA from the selected non-production project's authenticated Database SSL settings. Keep it outside Git and record only redacted provenance/checksum.
- [ ] Store the CA and `DATABASE_URL` through the approved non-production secret boundary. Require exactly one `sslmode=verify-full`, the matching container-local ephemeral `sslrootcert`, the selected non-production host, and no embedded password.
- [ ] Apply ordered repository migrations through a disposable Supabase CLI worktree linked to the shared non-production project. Review dry-run output before authorization.
- [ ] Run migrated-schema verification with the explicit isolated-target acknowledgement. Record only PostgreSQL major version, migration versions, test counts, and pass/fail.

### Runtime role and RLS

- [ ] Apply the administrator-only runtime-role setup and set `rotrack_runtime`'s password interactively.
- [ ] Run the read-only role audit as `rotrack_runtime`; all required least-privilege booleans must be true.
- [ ] Keep `BYPASSRLS` only for the pooled Spring JDBC boundary; verify ownership-scoped Spring queries and browser/Data API RLS independently.
- [ ] Create two disposable non-production users through Supabase Auth and confirm signup-trigger profiles without recording emails/UUIDs.
- [ ] Repeat the two-user Data API matrix and Spring Work/Rot ownership matrix. Foreign reads/stops and forged inserts must fail as contracted.

### Free-plan pause and backup safeguards

- [ ] Assign an owner for Free-project low-activity pause warnings and resume/recovery. Supabase documents that low-activity Free projects may auto-pause after seven days; see the official [pausing documentation](https://supabase.com/docs/guides/platform/free-project-pausing).
- [ ] Record that Free projects do not include automatic daily backups or PITR. Do not claim either is configured.
- [ ] Before production promotion, maintain encrypted, access-controlled off-site logical exports from [`supabase db dump`](https://supabase.com/docs/reference/cli/supabase-db-dump), with retention and a successful restore rehearsal, or obtain explicit product-owner data-loss risk acceptance. See the official [production checklist](https://supabase.com/docs/guides/deployment/going-into-prod).

### Connection budget

- [ ] Obtain the selected Supabase plan/pooler connection cap through the authenticated provider path.
- [ ] Set non-production pool/replica inputs and include revision overlap plus migration/operations reserve.
- [ ] Verify:

  ```text
  DATABASE_MAXIMUM_POOL_SIZE × maximum Container App replicas
    + migration/operations reserve
    <= approved non-production database capacity
  ```

- [ ] Keep `DATABASE_MINIMUM_IDLE=0` unless a reviewed capacity decision says otherwise.

## 3. Platform-neutral image and Azure Container App

- [ ] Build the reviewed Linux/amd64 OCI-compatible image and record its immutable registry digest, manifest media type, and architecture—not a tag or local image ID.
- [ ] Confirm non-root UID/GID `10001:10001`, Java 21, port `8080`, no source/secrets/browser state, runtime CA injection, graceful shutdown, liveness/readiness probes, and that application writes are limited to `/tmp`. The image is locally read-only-root compatible, but ACA enforcement is not claimed; compensate with non-root execution, no remote debug shell, least-privilege managed identity, and secret isolation.
- [ ] If ACR is selected, confirm the target Container App managed identity can pull only the approved image repository/digest. ACR choice does not change the OCI contract.
- [ ] Confirm managed environment `rotrack-nonproduction-env` inside resource group `rotrack-nonproduction` and Container App `rotrack-api-nonproduction` before mutation. The production managed environment/resource group/app must be separate.
- [ ] Configure HTTPS ingress to port `8080`; exact non-production CORS; `/api/v1/health` liveness; `/api/v1/readiness` readiness; and runtime secret/CA injection.
- [ ] Configure Consumption scaling. Min replicas `0` is accepted initially. Before production promotion, record at least 10 scale-from-zero trials; keep production at `0` only with explicit product-owner acceptance when p95 readiness is at most 30 seconds and no trial exceeds 60 seconds, otherwise set production minimum replicas to `1`.
- [ ] Record that Azure budget alerts are notifications, not a hard spending cap, and that cost/credit-expiry data can be delayed. Do not claim alerts are configured until observed.
- [ ] Configure revision/traffic behavior so an unhealthy rollout cannot cause replacement or database-connection churn.
- [ ] Verify the running revision reports the reviewed image digest and release ID.

## 4. Vercel Preview and GitHub environment

- [ ] Confirm the one Vercel project and select its built-in Preview environment; do not create a staging project.
- [ ] Confirm the logical GitHub `nonproduction` environment has required approval/branch restrictions and non-production-only values. Settings are external evidence.
- [ ] Configure only the three frontend names: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_KEY`, and `NEXT_PUBLIC_API_URL`.
- [ ] Confirm the Supabase URL/key are the shared non-production project and the API URL ends in `/api/v1`.
- [ ] Build/deploy a Preview and confirm the backend CORS allowlist contains only explicitly approved HTTPS Preview origin(s), never a wildcard or production origin.
- [ ] Confirm browser assets refer only to non-production Auth/API endpoints.
- [ ] Record source-commit provenance. Production must build the same reviewed commit with production-scoped `NEXT_PUBLIC_*` values; do not promote Preview bytes because those values are embedded at build time.

## 5. Unauthenticated smoke commands

Set origins in a private shell; never save populated output:

```bash
export ROTRACK_NONPRODUCTION_API_ORIGIN='<non-production-api-origin>'
export ROTRACK_NONPRODUCTION_FRONTEND_ORIGIN='<approved-preview-origin>'

curl --silent --show-error --fail-with-body \
  "$ROTRACK_NONPRODUCTION_API_ORIGIN/api/v1/health" | jq -e '. == {"status":"ok"}'
curl --silent --show-error --fail-with-body \
  "$ROTRACK_NONPRODUCTION_API_ORIGIN/api/v1/readiness" | jq -e '. == {"status":"ready"}'
curl --silent --show-error --fail-with-body \
  "$ROTRACK_NONPRODUCTION_FRONTEND_ORIGIN/" >/dev/null
```

Check allowed and denied CORS origins with the exact preflight contract in [`startup-and-health.md`](../startup-and-health.md). Record status codes and allow/deny results only.

Then run the authenticated suite with two external disposable-user storage states:

```bash
cd frontend
ROTRACK_E2E_BASE_URL="$ROTRACK_NONPRODUCTION_FRONTEND_ORIGIN" \
ROTRACK_E2E_EXPECTED_API_URL="$ROTRACK_NONPRODUCTION_API_ORIGIN/api/v1" \
ROTRACK_E2E_REQUIRE_AUTH=1 \
ROTRACK_E2E_USER_A_STORAGE_STATE='<external-user-a-state-path>' \
ROTRACK_E2E_USER_B_STORAGE_STATE='<external-user-b-state-path>' \
  npm run e2e
```

Require four passed Chromium tests, zero skips/unexpected/flaky results. Confirm Work/Rot start, reload/navigation, close/reopen restoration, explicit stop, dashboard deltas, and two-user isolation. Do not claim any result until observed against the selected non-production deployment.

## 6. Release safeguards

- [ ] Structured application logging is enabled with runtime `staging` metadata (from logical GitHub `nonproduction`) and the image digest as service version.
- [ ] Collector redaction, telemetry, alert routing, and access/retention controls are observed with synthetic sentinels; no private data is used.
- [ ] Health, readiness, latency, error, auth, connection, replica/restart, migration, frontend exception, cold-start, Free pause/resume, logical-export freshness/restore, and budget/credit-expiry signals have owners and tested routes. Azure budget alerts remain notifications, not a hard spending cap.
- [ ] The trusted fleet-wide/authentication-adjacent rate-limit boundary and failure-mode tests pass.
- [ ] Production promotion remains blocked until non-production smoke, safeguards, exact prior/candidate rollback rehearsal, and all release approvals pass.

## 7. Failure, rollback, and teardown

- [ ] A migration identity/TLS/RLS failure, pause/resume failure, budget notification, mutable image, wildcard CORS, wrong environment, unhealthy readiness, cross-user access, or unexpected cold-start timeout is an unconditional release blocker. Only missing/stale logical-export or failed restore-rehearsal evidence may proceed under a separately recorded product-owner data-loss risk acceptance.
- [ ] Migrations are database-first and backward-compatible. Roll back the prior compatible image digest and Vercel deployment only when the matrix permits; never edit migration history or automatically reverse migrations.
- [ ] Do not mass-stop active timer sessions during rollback.
- [ ] Preserve redacted evidence before teardown. Remove only the authorized non-production Preview deployment, Container App revision/resources, temporary secret/configuration material, disposable users, and external auth state.
- [ ] Confirm no non-production route, replica, secret access, disposable data, or billable resource remains, or record an approved retention decision.

The production checklist must repeat these controls against `rotrack-prod`, Vercel Production, GitHub target `production`, managed environment `rotrack-production-env` inside `rotrack-production`, and `rotrack-api-production`; no production execution is implied by this document.
