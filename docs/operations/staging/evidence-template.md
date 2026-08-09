# Non-production deployment evidence — template

Copy this template to an approved evidence system only after an authorized non-production run. Checked-in copies must remain blank. Public credential-free Preview/frontend and API URLs may be recorded only when approved. Never record project refs, subscription/resource IDs, credentials, keys, bearer tokens, user emails/UUIDs, CA contents, browser auth-state paths/content, private application data, or query-bearing URLs.

## Authorization and separation

- Date/time UTC: pending
- Operator/reviewer roles (no personal contact data): pending
- Explicit non-production authorization: pending
- Shared existing non-production/dev Supabase project selected: pending
- `rotrack-prod` untouched: yes / no / pending
- One Vercel project with built-in Preview selected: pending
- Logical GitHub environment `nonproduction` and protections observed: pending
- Azure managed environment `rotrack-nonproduction-env` inside resource group `rotrack-nonproduction` / Container App `rotrack-api-nonproduction` readback: pending
- Production resource group/app untouched: yes / no / pending
- Remote resources configured by this run: pending

## Artifact and configuration

- Source revision: pending
- OCI-compatible image registry digest, manifest media type, and architecture (registry/account redacted): pending
- Image Linux/amd64, non-root, writes limited to `/tmp`, port 8080, probes, graceful shutdown, CA injection: pending
- ACA read-only-root enforcement: unsupported/unverified; non-root/debug/identity/secret compensating controls reviewed: pending
- ACR managed-identity pull (only if selected): pending / not applicable
- Non-production Supabase CA provenance/TLS `verify-full`: pending
- Exact frontend environment-name check: pending
- Exact backend environment/secret-name check: pending
- Exact HTTPS Preview CORS origin(s): pending
- Azure Consumption scaling/min replicas plus 10-trial cold-start p95/maximum and production acceptance: pending
- Database pool/replica/rollout-overlap arithmetic: pending
- Canonical mapping observed: GitHub/ACA `nonproduction` → runtime/telemetry `staging`; production → `production`: pending
- Structured logging `staging` metadata and service-version-to-image-digest deployment/readback binding: pending
- Budget/credit-expiry notifications (not a hard spending cap; delayed data considered): pending
- Free-project pause-warning/resume owner and outcome: pending
- Encrypted off-site logical export, retention, and restore rehearsal or explicit product-owner data-loss risk acceptance: pending
- Automatic daily backups/PITR: not part of Free topology

## Database/Auth boundary

- Ordered migration dry run/apply/list result: pending
- Migrated-schema test count/result/PostgreSQL major: pending
- `rotrack_runtime` audit booleans all true: pending
- `BYPASSRLS` rationale reviewed and Spring ownership boundary tested: pending
- Data API RLS policy/read/write matrix: pending
- Disposable signup-trigger profiles: pending
- Two-user Spring Work/Rot ownership matrix: pending
- Disposable user/token cleanup: pending

## Deploy and smoke

- Container App managed environment/revision reports exact image digest/release ID: pending
- Preview deployment status, immutable ID, and reviewed source-commit provenance: pending
- Production frontend plan acknowledges separate same-commit build for production-scoped `NEXT_PUBLIC_*` values: pending
- Liveness `200` stable body: pending
- Readiness `200` stable body: pending
- Frontend status: pending
- Allowed CORS exact-origin result: pending
- Denied CORS no-allow-origin result: pending
- Authenticated Playwright result: pending
- Work/Rot restore/explicit-stop/dashboard result: pending
- Cross-user isolation result: pending
- Cold-start result and interpretation: pending
- Logs/collector checked for secret/private-content leakage: pending

## Rollback and teardown

- Prior image digest/frontend deployment compatibility checked: pending
- Revision/traffic rollback control: pending
- Migration rollback limitation acknowledged: pending
- Budget/credit-expiry notification route tested (not a hard spending cap): pending
- Free pause/resume and logical-export/restore safeguards: pending
- Teardown owner/date: pending
- Non-production temporary resources/auth state removed or retained by approval: pending
- Orphaned replicas/routes/secrets/disposable data check: pending

## Commands and concise results

```text
Local/container contract commands
Result: pending

Supabase migration and role/RLS commands from deploy/staging/README.md
Result: pending

Azure managed environment/Container Apps deployment/readback commands
Result: pending

Vercel Preview/GitHub nonproduction checks
Result: pending

Smoke commands from docs/operations/staging/checklist.md
Result: pending
```

## Blockers and exceptions

- Blockers: pending
- Approved exceptions and expiry: pending
- Residual risks: pending
- Reviewer decision: pending
