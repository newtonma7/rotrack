# Staging deployment contract

This directory is the staging-only overlay for Vercel, ECS/Fargate, and a separate Supabase project. It contains placeholders and validation tooling, not provisioned infrastructure. **No remote staging environment was created while preparing this contract:** no separately authorized staging project, AWS account resources, Vercel project, or approved image digest was available. Never point these commands at development or production.

The checked-in templates deliberately retain `__ROTRACK_STAGING_*__` sentinels. `validate.sh templates` requires them; `render.sh` resolves only the deployment/task inputs into ignored, mode-`0600` files and then invokes the fail-closed deployment validator.

## Files

- `templates/frontend.env.template` — exact Vercel frontend names.
- `templates/backend.env.template` — exact Spring runtime names.
- `templates/deployment.env.template` — non-secret local render inputs, including three project refs used only to prove separation.
- `templates/ecs-task-definition.json.template` — Fargate task definition pinned to an image digest.
- `templates/runtime-role.sql.template` — administrator-only staging role/grant setup.
- `templates/runtime-role-audit.sql.template` — read-only boolean audit run while authenticated as `rotrack_runtime`.
- `validate.sh` / `render.sh` / `tests/validate.sh` — static, render-time, and synthetic validation.
- `aws-preflight.sh` — read-only caller, task-role, required-secret-access, and ECS staging-tag verification before registration/update.

Rendered files belong under `deploy/staging/rendered/`, which is ignored. They may contain project/account identifiers and secret ARNs, so do not paste or commit them. Secret **values** never enter these templates or the renderer.

## Exact environment and secret names

The dedicated Vercel staging project defines exactly:

```text
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_KEY
NEXT_PUBLIC_API_URL
```

`NEXT_PUBLIC_SUPABASE_KEY` is the staging anon/publishable browser key, never a service-role key. `NEXT_PUBLIC_API_URL` includes `/api/v1`.

The ECS task exposes exactly these Spring names:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
DATABASE_CA_CERTIFICATE_PEM
DATABASE_CA_CERTIFICATE_PATH
DATABASE_CONNECTION_TIMEOUT_MS
DATABASE_POOL_VALIDATION_TIMEOUT_MS
DATABASE_MAXIMUM_POOL_SIZE
DATABASE_MINIMUM_IDLE
READINESS_CACHE_TTL
SUPABASE_JWKS_URI
SUPABASE_ISSUER_URI
SUPABASE_JWT_AUDIENCE
CORS_ALLOWED_ORIGINS
PORT
SERVER_SHUTDOWN
SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE
LOGGING_STRUCTURED_FORMAT_CONSOLE
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY
LOGGING_LEVEL_ORG_HIBERNATE_SQL
LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND
SPRING_JPA_SHOW_SQL
```

Create these exact AWS Secrets Manager secret names, with access limited to the staging task execution role:

```text
rotrack/staging/backend/DATABASE_URL
rotrack/staging/backend/DATABASE_USERNAME
rotrack/staging/backend/DATABASE_PASSWORD
rotrack/staging/backend/DATABASE_CA_CERTIFICATE_PEM
rotrack/staging/backend/SUPABASE_JWKS_URI
rotrack/staging/backend/SUPABASE_ISSUER_URI
```

The task definition receives their full ARNs through local `ROTRACK_STAGING_*_SECRET_ARN` inputs. It must never receive a development or production secret ARN. `DATABASE_URL` must use the staging session/direct endpoint with exactly one `sslmode=verify-full` and `sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt`. The official provider CA is injected as PEM and materialized into task-local `/tmp` by the non-root entrypoint; it is never committed to or baked into the image. Do not capture a certificate from an unverified connection or use `sslmode=require`.

## Render and validate

Export every name on the left side of `templates/deployment.env.template` in a private operator shell. Values come from the authorized staging resources; project refs are used for comparison and are not committed. Then run:

```bash
./deploy/staging/validate.sh templates
./deploy/staging/render.sh
./deploy/staging/validate.sh deployment deploy/staging/rendered
```

The renderer refuses missing variables, newlines, unresolved sentinels, mutable image tags, duplicate Supabase refs, non-HTTPS origins, inconsistent CORS, identical task/execution roles, non-staging ECS names, a staging AWS account equal to the protected production account, and an unsafe connection budget. The budget is:

```text
DATABASE_MAXIMUM_POOL_SIZE × (maximum ECS task count × 2 rollout generations)
  <= staging database connection limit - migration/operations reserve
```

Set the limit to the lower approved staging database/pooler capacity, not a guessed plan maximum. Keep `DATABASE_MINIMUM_IDLE=0`; reserve capacity for a migration connection, provider/admin operations, and incident access. Scaling beyond the validated maximum requires re-rendering and revalidation first.

## Deployment commands

Run only after completing `docs/operations/staging/checklist.md`, confirming all three Supabase refs are distinct, and receiving explicit authorization for this non-production staging target.

### Database first

The repository stores migrations under `database/migrations/`, not the Supabase CLI's `supabase/migrations/` workdir. Create a disposable CLI workdir and copy the reviewed files there so the project link cannot be confused with development. The commands prompt for the staging database password; do not put it on the command line.

```bash
export ROTRACK_STAGING_SUPABASE_PROJECT_REF='<staging-project-ref>'
ROTRACK_STAGING_CLI_WORKDIR="$(mktemp -d)"
trap 'rm -rf "$ROTRACK_STAGING_CLI_WORKDIR"' EXIT
supabase init --workdir "$ROTRACK_STAGING_CLI_WORKDIR"
mkdir -p "$ROTRACK_STAGING_CLI_WORKDIR/supabase/migrations"
cp database/migrations/*.sql "$ROTRACK_STAGING_CLI_WORKDIR/supabase/migrations/"
diff -qr database/migrations "$ROTRACK_STAGING_CLI_WORKDIR/supabase/migrations"

supabase projects list --output json \
  | jq -e --arg ref "$ROTRACK_STAGING_SUPABASE_PROJECT_REF" \
      '[.[] | select(.id == $ref)] | length == 1'
supabase link \
  --workdir "$ROTRACK_STAGING_CLI_WORKDIR" \
  --project-ref "$ROTRACK_STAGING_SUPABASE_PROJECT_REF"
supabase migration list --workdir "$ROTRACK_STAGING_CLI_WORKDIR" --linked
supabase db push --workdir "$ROTRACK_STAGING_CLI_WORKDIR" --linked --include-all --dry-run
# Review that only ordered repository migrations are pending, then authorize the mutation:
supabase db push --workdir "$ROTRACK_STAGING_CLI_WORKDIR" --linked --include-all
supabase migration list --workdir "$ROTRACK_STAGING_CLI_WORKDIR" --linked
rm -rf "$ROTRACK_STAGING_CLI_WORKDIR"
trap - EXIT
```

Apply `templates/runtime-role.sql.template` through an authorized administrator `psql` staging connection after migrations, set its password interactively with `\password rotrack_runtime`, then open a separate connection authenticated as `rotrack_runtime` and run `templates/runtime-role-audit.sql.template`. Every audit boolean must be true; never rerun the administrator-only setup file as the runtime role. Spring uses `BYPASSRLS` because pooled JDBC requests do not carry `auth.uid()`; the role has only `SELECT/INSERT/UPDATE` on `time_entries`, while Spring's UUID-scoped repository queries remain the authorization boundary. RLS stays enabled and independently tested for browser/Data API access.

### Backend ECS/Fargate

The image input must be an immutable registry URI ending in `@sha256:<64 lowercase hex characters>`. The container must run non-root, include `wget` for the task liveness command, materialize the injected official CA at the declared `/tmp` path, and pass its Lane B artifact checks before registration.

Keep the validated `ROTRACK_STAGING_*` values exported in the same private shell used by `render.sh`; do not `source` a rendered file. Run `./deploy/staging/aws-preflight.sh` immediately before task registration. It fails unless the caller account matches the staging roles and differs from production, the task role has no attached/inline policies, the execution role can read the six exact staging secret inputs, and the existing service has `Environment=staging`. Before mutation, compare `aws sts get-caller-identity --query Account --output text` with the staging account embedded in the validated role ARNs and confirm it differs from `ROTRACK_PRODUCTION_AWS_ACCOUNT_ID`. Read back ECS resource tags and require `Environment=staging`. The task role must have no attached or inline AWS policies; the execution role may have only image/log permissions plus `secretsmanager:GetSecretValue` for the six exact validated secret ARNs. Record boolean results, never policy bodies or ARNs.

```bash
TASK_DEFINITION_ARN="$(aws ecs register-task-definition \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cli-input-json file://deploy/staging/rendered/ecs-task-definition.json \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text)"

aws elbv2 modify-target-group \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --target-group-arn "$ROTRACK_STAGING_ALB_TARGET_GROUP_ARN" \
  --health-check-protocol HTTP \
  --health-check-path /api/v1/readiness \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher HttpCode=200

aws application-autoscaling register-scalable-target \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id "service/$ROTRACK_STAGING_ECS_CLUSTER_NAME/$ROTRACK_STAGING_ECS_SERVICE_NAME" \
  --min-capacity "$ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT" \
  --max-capacity "$ROTRACK_STAGING_ECS_MAX_TASK_COUNT"

aws application-autoscaling describe-scalable-targets \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-ids "service/$ROTRACK_STAGING_ECS_CLUSTER_NAME/$ROTRACK_STAGING_ECS_SERVICE_NAME" \
  | jq -e --argjson maximum "$ROTRACK_STAGING_ECS_MAX_TASK_COUNT" \
      '.ScalableTargets | length == 1 and .[0].MaxCapacity == $maximum'

aws ecs update-service \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cluster "$ROTRACK_STAGING_ECS_CLUSTER_NAME" \
  --service "$ROTRACK_STAGING_ECS_SERVICE_NAME" \
  --task-definition "$TASK_DEFINITION_ARN" \
  --platform-version 1.4.0 \
  --desired-count "$ROTRACK_STAGING_ECS_DESIRED_TASK_COUNT" \
  --deployment-configuration 'deploymentCircuitBreaker={enable=true,rollback=true},minimumHealthyPercent=100,maximumPercent=200' \
  --force-new-deployment

aws ecs wait services-stable \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cluster "$ROTRACK_STAGING_ECS_CLUSTER_NAME" \
  --services "$ROTRACK_STAGING_ECS_SERVICE_NAME"
```

Tasks run in private subnets with no public IP. The ALB is the only ingress to the container port; the task security group accepts that port only from the ALB security group. The task uses outbound TLS to Supabase Auth/JWKS, PostgreSQL, and CloudWatch. The public listener redirects HTTP to HTTPS and routes application traffic only to ready targets. Container liveness is `/api/v1/health`; ALB readiness is `/api/v1/readiness`.

### Frontend Vercel

Use a dedicated Vercel project whose Vercel “production” environment is still rotrack **staging**. The renderer requires staging and production team slugs, project names, organization IDs, and project IDs to be distinct. Link only with the validated staging names, then fail closed unless Vercel's local readback matches the protected staging IDs before any environment mutation or `--prod` deployment:

```bash
cd frontend
vercel link \
  --scope "$ROTRACK_STAGING_VERCEL_TEAM_SLUG" \
  --project "$ROTRACK_STAGING_VERCEL_PROJECT_NAME"
jq -e \
  --arg org "$ROTRACK_STAGING_VERCEL_ORG_ID" \
  --arg project "$ROTRACK_STAGING_VERCEL_PROJECT_ID" \
  '.orgId == $org and .projectId == $project' \
  .vercel/project.json
vercel env add NEXT_PUBLIC_SUPABASE_URL production
vercel env add NEXT_PUBLIC_SUPABASE_KEY production
vercel env add NEXT_PUBLIC_API_URL production
vercel pull --yes --environment=production
vercel build --prod
vercel deploy --prebuilt --prod
```

Each `vercel env add` prompt receives only its staging value. Confirm the linked IDs with the mandatory `jq` gate above and separately inspect the displayed project/team names; never commit `.vercel/project.json` or record its IDs. Set the ECS `CORS_ALLOWED_ORIGINS` to the single stable staging frontend origin, not preview wildcards. Preview deployments must use a separately approved exact origin or remain API-inaccessible.

## Smoke and evidence

Use the exact redacted-safe probes in `docs/operations/staging/checklist.md`. Store only status codes, stable response bodies, image digest, redacted origins, migration versions, boolean grant/RLS results, task counts, and pool arithmetic in the evidence template. Public, credential-free staging frontend/API URLs may be recorded because the milestone requires them. Project refs, account IDs, role/secret ARNs, token values, user emails, database hosts, query-bearing URLs, and storage-state paths or contents remain redacted.
