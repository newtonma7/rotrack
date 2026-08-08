# Staging deployment evidence — template

Copy this template to an approved evidence system only after an authorized staging run. Checked-in copies must remain blank. Public, credential-free staging frontend/API URLs are required release evidence and may be recorded. Never record project refs, account IDs, role/secret ARNs, database hosts or query-bearing URLs, Vercel project IDs, credentials, keys, bearer tokens, user emails/UUIDs, CA contents, browser auth-state paths/content, or private application data.

## Authorization and separation

- Date/time UTC: pending
- Operator/reviewer roles (no personal contact data): pending
- Explicit non-production staging authorization: pending
- Staging Supabase distinct from development and production (three-ref validator): pending
- Supabase CLI selected exactly one authorized staging project: pending
- Vercel project/team plus organization/project ID readback confirmed dedicated staging: pending
- AWS cluster/service/target group confirmed staging-only: pending
- Production touched: no
- Development Supabase touched: no

## Artifact and configuration

- Source revision: pending
- Public staging frontend URL (no query/user information): pending
- Public staging API URL (no query/user information): pending
- Backend image digest (digest only; registry/account redacted): pending
- Image non-root/read-only-root/CA/wget/artifact checks: pending
- Official provider CA source verified and local checksum matched: pending
- TLS `verify-full` plus explicit CA path: pending
- Exact frontend environment-name and protected Vercel identity check: pending
- Exact backend environment/secret-name check: pending
- Restricted single-origin CORS: pending
- ECS private networking/ALB-only ingress/TLS: pending
- Container liveness `/api/v1/health`: pending
- ALB readiness `/api/v1/readiness`: pending
- Desired/maximum task counts: pending
- Per-task maximum/minimum pool: pending
- Provider-approved connection limit and operations reserve: pending
- Rollout-peak pool arithmetic passed: pending
- Autoscaling maximum registered/read back: pending
- Template static validation: pending
- Deployment render validation: pending

## Database/Auth boundary

- Ordered migration dry run (`001`, `002` only): pending
- Ordered migration apply/list result: pending
- Migrated-schema test count/result/PostgreSQL major: pending
- `rotrack_runtime` audit booleans all true: pending
- `BYPASSRLS` rationale reviewed (Spring ownership boundary): pending
- Data API RLS enabled/policy count: pending
- Disposable signup-trigger profiles: pending
- Two-user Data API allow/deny matrix: pending
- Two-user Spring Work/Rot ownership matrix: pending
- Disposable user/token cleanup: pending

## Deploy and smoke

- ECS task registration/service stabilization: pending
- Vercel staging build/deploy: pending
- Liveness status/body (`200`, stable status only): pending
- Readiness status/body (`200`, stable status only): pending
- Frontend status: pending
- Allowed CORS exact-origin result: pending
- Denied CORS no-allow-origin result: pending
- Authenticated Playwright result/test count: pending
- Work/Rot restore/explicit-stop/dashboard result: pending
- Cross-user isolation result: pending
- Logs checked for secret/private-content leakage: pending

## Rollback and teardown

- Prior image digest/deployment compatibility checked: pending
- ECS circuit breaker configured: pending
- Migration rollback limitation acknowledged: pending
- Teardown owner/date: pending
- ECS/Vercel/Supabase staging resources removed or retained by approval: pending
- Local rendered config, CA, Auth state, and shell variables removed: pending
- Orphaned tasks/routes/secrets/disposable data check: pending

## Commands and concise results

```text
./deploy/staging/validate.sh templates
Result: pending

./deploy/staging/render.sh
Result: pending

./deploy/staging/validate.sh deployment deploy/staging/rendered
Result: pending

Supabase migration commands from deploy/staging/README.md
Result: pending

AWS ECS/ALB commands from deploy/staging/README.md
Result: pending

Vercel commands from deploy/staging/README.md
Result: pending

Smoke commands from docs/operations/staging/checklist.md
Result: pending
```

## Blockers and exceptions

- Blockers: pending
- Approved exceptions and expiry: pending
- Residual risks: pending
- Reviewer decision: pending
