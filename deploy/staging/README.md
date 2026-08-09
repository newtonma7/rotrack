# Non-production deployment target

This directory documents the approved non-production deployment boundary. The path name is retained for repository compatibility; **staging is not a third Supabase or a dedicated Vercel project**. Development and approved environment-scoped authenticated E2E use the existing non-production/dev Supabase Free project. Credential-free pull-request CI uses isolated disposable PostgreSQL and never connects to hosted Supabase. Vercel Preview is the non-production environment in the one Vercel project. Production uses the separately created `rotrack-prod` Supabase Free project and that same Vercel project's Production environment.

This is the boundary contract; the executable Azure runbook and 2026-08-09 non-production checkpoint are in [`docs/operations/azure-nonproduction.md`](../../docs/operations/azure-nonproduction.md). Azure/ACR/Vercel non-production infrastructure, immutable digest readback, HTTPS health/readiness, and exact CORS are observed. GitHub protection, authenticated smoke, alert routing, cold-start trials, backup/restore, rollback, and all production resources remain open. Never point non-production commands at `rotrack-prod` or production resources.

## Target boundary

| Concern | Non-production | Production |
|---|---|---|
| Supabase | Existing non-production/dev Free project, shared by development and approved environment-scoped authenticated E2E | Newly created `rotrack-prod` Free project |
| Vercel | Preview environment | Production environment |
| GitHub | Target logical `nonproduction` environment | Target logical `production` environment |
| Azure managed environment | `rotrack-nonproduction-env` | `rotrack-production-env` |
| Azure resource group | `rotrack-nonproduction` | `rotrack-production` |
| Azure Container App | `rotrack-api-nonproduction` | `rotrack-api-production` |
| Compute | Container Apps Consumption, initially min replicas `0` accepted | Container Apps Consumption, initially min replicas `0` accepted |

The non-production sharing tradeoff is intentional for development and approved environment-scoped authenticated E2E only. Credential-free PR CI uses isolated disposable PostgreSQL, not the hosted non-production project. Production migrations, users, secrets, API origins, and browser state remain separate. Do not create a third Supabase project or a dedicated staging Vercel project to satisfy an old checklist.

## Repository artifact status

The platform-neutral backend artifact is a Linux/amd64 OCI-compatible image. Its contract is in [`deploy/container/CONTRACT.md`](../container/CONTRACT.md): immutable registry digest/media-type readback, non-root UID/GID `10001:10001`, writes limited to `/tmp`, port `8080`, liveness/readiness, graceful shutdown, runtime CA injection, and exact `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (Elastic Common Schema, not AWS Elastic Container Service). The image is locally read-only-root compatible, but ACA enforcement is not claimed.

`deploy/ecs/base/*.json`, `templates/ecs-task-definition.json.template`, `validate.sh`, `render.sh`, `tests/validate.sh`, and `aws-preflight.sh` are checked-in legacy/unselected AWS/ECS artifacts and are not the active path. Use `deploy/azure/` and `scripts/azure/`; failures of legacy AWS validators against the approved two-project/one-Vercel/Azure architecture are not Azure deployment evidence.

If a registry integration must be selected, prefer Azure Container Registry (ACR) with managed identity for the Container Apps pull. ACR is only the Azure delivery integration; the OCI-compatible image remains vendor-neutral and is identified by an immutable registry digest.

## Supabase Free-plan operating safeguards

Both target Supabase projects are Free-plan projects. The official [Free project pausing documentation](https://supabase.com/docs/guides/platform/free-project-pausing) says low-activity Free projects may be automatically paused after 7 days; assign an owner for pause warnings and resume/recovery. Supabase's [database backup documentation](https://supabase.com/docs/guides/platform/backups) says automatic daily backups are a Pro/Team/Enterprise feature and recommends regular exports for Free projects, so this topology requires encrypted, access-controlled off-site logical exports using [`supabase db dump`](https://supabase.com/docs/reference/cli/supabase-db-dump). PITR is not part of this Free topology.

No pause alert, resume operation, export retention, backup configuration, PITR, or restore evidence is claimed here. Before production promotion, complete a restore rehearsal from a retained logical export or record an explicit product-owner data-loss risk acceptance.

## Required logical environment inputs

Keep populated values in the approved GitHub Environment or an operator-owned secret store; do not commit them.

Non-production (`nonproduction`):

- one Supabase project URL, anon/publishable key, database/TLS inputs, issuer/JWKS configuration, and exact Preview API CORS origin(s);
- the immutable backend image digest and non-secret release ID;
- Azure subscription/resource-group/app identity and, if selected, ACR managed-identity wiring;
- Vercel Preview build inputs for the single Vercel project.

Production (`production`):

- the `rotrack-prod` Supabase URL/key/database/TLS inputs and exact Production API CORS origin;
- a separately approved immutable backend image digest and release ID;
- production Azure resource-group/app identity and, if selected, production ACR managed-identity wiring;
- Vercel Production inputs in the same Vercel project.

Do not place database passwords, service-role keys, CA contents, bearer tokens, or Playwright storage state in GitHub PR variables, frontend variables, evidence, or this repository. `NEXT_PUBLIC_SUPABASE_KEY` is only the anon/publishable browser key. `NEXT_PUBLIC_API_URL` includes `/api/v1`.

## Database-first preparation

Apply ordered migrations to exactly one selected Supabase project per environment. For local/non-production work, the selected project is the existing shared non-production/dev project; production uses `rotrack-prod` only after the release gate passes.

Use a disposable Supabase CLI worktree so the linked project cannot be confused with another target. The command shape is illustrative and requires private operator authorization:

```bash
export ROTRACK_TARGET_SUPABASE_PROJECT_REF='<one-authorized-project-ref>'
CLI_WORKDIR="$(mktemp -d)"
trap 'rm -rf "$CLI_WORKDIR"' EXIT
supabase init --workdir "$CLI_WORKDIR"
mkdir -p "$CLI_WORKDIR/supabase/migrations"
cp database/migrations/*.sql "$CLI_WORKDIR/supabase/migrations/"
diff -qr database/migrations "$CLI_WORKDIR/supabase/migrations"
supabase link --workdir "$CLI_WORKDIR" \
  --project-ref "$ROTRACK_TARGET_SUPABASE_PROJECT_REF"
supabase migration list --workdir "$CLI_WORKDIR" --linked
supabase db push --workdir "$CLI_WORKDIR" --linked --include-all --dry-run
# Review the ordered migration plan before a separately authorized apply.
# supabase db push --workdir "$CLI_WORKDIR" --linked --include-all
rm -rf "$CLI_WORKDIR"
trap - EXIT
```

Use an administrator identity for migrations and the narrow `rotrack_runtime` role for the Spring API. Keep `BYPASSRLS` and ownership-scoped Spring queries as documented in `arch.plan.md`; independently test browser/Data API RLS with anon/authenticated identities. Record only redacted migration versions, role-audit booleans, and test outcomes.

## Azure Container Apps integration

Before any mutation, confirm the selected subscription, target managed environment, resource group, app name, environment label, and logical GitHub environment match. The managed environment is the Azure security boundary and must be separate for non-production and production, each inside its matching resource group. No command below is evidence until it is actually run and recorded in a redacted release record.

1. Build the OCI-compatible image once from the reviewed commit and retain the immutable registry digest, manifest media type, and architecture readback.
2. If ACR is selected, push that digest to the target ACR and grant the target Container App's managed identity pull access. Do not replace the digest with a mutable tag.
3. Create/update only the matching managed environment, resource group, and Container App: `rotrack-nonproduction-env` inside `rotrack-nonproduction` with `rotrack-api-nonproduction` for `nonproduction`, or `rotrack-production-env` inside `rotrack-production` with `rotrack-api-production` for `production`.
4. Configure HTTPS ingress to container port `8080`, non-root execution, application writes limited to `/tmp`, no remote debug shell, least-privilege identity/secret boundaries, `/api/v1/health` liveness, `/api/v1/readiness` readiness, graceful shutdown, exact CORS, and runtime injection of the official Supabase CA and other secrets. Do not claim ACA read-only-root enforcement until a supported provider control is implemented and observed.
5. Configure Consumption scaling. Min replicas `0` is accepted initially. Before production promotion, run at least 10 non-production scale-from-zero trials; keep production at `0` only with explicit product-owner acceptance when p95 readiness is at most 30 seconds and no trial exceeds 60 seconds, otherwise set production minimum replicas to `1`. Azure budget alerts are notifications, not a hard spending cap, and cost/credit-expiry data can be delayed. Budget and credit-expiry notifications are required before production promotion, but do not claim they are configured here.
6. Verify the running revision reports the exact image registry digest and that `ROTRACK_SERVICE_VERSION` equals the approved digest-derived release value. Application startup does not enforce this equality; the ACA deployment/readback adapter must. A successful template render or local image build is not remote deployment evidence.

Keep database connection arithmetic within the selected Supabase plan/pooler limit:

```text
DATABASE_MAXIMUM_POOL_SIZE × maximum Container App replicas
  + migration/operations reserve
  <= approved non-production or production database capacity
```

Include revision overlap when a rollout can run old and new replicas together. Recalculate before changing replica limits or rollout behavior.

## Vercel and GitHub integration

Use one Vercel project. Let ordinary branch/PR deployments use Vercel Preview with the `nonproduction` GitHub environment's public values. GitHub/ACA `nonproduction` maps to runtime/telemetry label `staging`; production maps to `production`. Because `NEXT_PUBLIC_*` values are embedded at build time, do not promote Preview bytes to Production. Build Vercel Production from the same reviewed source commit under the `production` GitHub environment, record its separate immutable deployment provenance, and verify its production-scoped values. Do not create or document a dedicated staging project.

Both GitHub environments must have protected approvals and separately scoped secrets/variables before they are treated as release controls. The names `nonproduction` and `production` are logical boundaries; their settings are not asserted here.

The Preview and Production frontend values must point to the matching Supabase project and API. The backend CORS allowlist must contain exact approved HTTPS origins; do not use a wildcard or assume every Vercel preview URL is trusted. If multiple Preview origins are needed, approve them explicitly and include them in the non-production contract without exposing production.

## Smoke, rollback, and evidence

Use the health, CORS, authenticated browser, release, monitoring, and incident contracts under `docs/operations/`. Run authenticated smoke only with two disposable non-production users and external storage-state files. Never use production credentials or real private data.

A release is database-first and requires a compatible prior image digest. Roll back the application image and Vercel deployment independently only when the compatibility matrix permits it; do not automatically reverse migrations or stop active timer sessions. A scale-to-zero wake-up is not by itself a failed deployment, but repeated cold-start timeout or readiness failure is a stop condition.

Record only redacted evidence: source commit, image digest, release ID, migration versions/checksums, health statuses, CORS allow/deny result, test counts, replica counts, pool arithmetic, alert IDs, and approval outcomes. Do not claim Azure, Vercel, GitHub, Supabase, ACR, monitoring, or billing controls are configured or verified without direct evidence.
