# Staging isolation, deployment, smoke, and teardown checklist

**Current status:** preparation only. No remote infrastructure was provisioned and no health/config evidence was observed. The unblock condition is an explicitly authorized, disposable non-production Supabase project plus dedicated Vercel/AWS staging resources and a reviewed backend image digest. Development and production are prohibited targets.

Use `deploy/staging/README.md` for the exact names, renderer, and deploy commands. Record results in `evidence-template.md` without identifiers or secrets.

## 1. Authorization and identity gate

- [ ] Record the change owner, staging authorization, maintenance window, and teardown owner outside Git.
- [ ] In a private shell, set `ROTRACK_STAGING_SUPABASE_PROJECT_REF`, `ROTRACK_DEVELOPMENT_SUPABASE_PROJECT_REF`, and `ROTRACK_PRODUCTION_SUPABASE_PROJECT_REF` from authoritative dashboards. Do not paste them into evidence.
- [ ] Run `./deploy/staging/render.sh`; it must reject equal or malformed refs.
- [ ] Resolve the selected staging ref through `supabase projects list --output json` as shown in the deployment runbook, without printing the project list into evidence.
- [ ] Confirm the project organization/environment labels say staging and that no development/production ref, database host, Vercel project, AWS cluster, service, target group, secret ARN, or DNS zone is selected.
- [ ] Compare the caller AWS account from `aws sts get-caller-identity` with the account embedded in the validated staging role ARNs; require both to differ from the protected production account input. Read ECS/ALB resource tags and require `Environment=staging` before mutation.
- [ ] Confirm the Supabase project contains only disposable staging users/data and has an assigned teardown date.

Stop immediately on an identity mismatch. Never “fix” the mismatch by changing the expected development or production ref.

## 2. Supabase staging database and Auth

### CA, TLS, and migrations

- [ ] Download the official CA from the selected staging project's Database SSL settings over the authenticated provider dashboard. Record only its redacted source and local checksum result; do not commit the certificate.
- [ ] Enable provider SSL enforcement after the CA is available.
- [ ] Store the official CA PEM only in `rotrack/staging/backend/DATABASE_CA_CERTIFICATE_PEM`; confirm the non-root entrypoint materializes it mode `0600` at `/tmp/rotrack-certs/supabase-db-ca.crt` without logging it.
- [ ] Store `DATABASE_URL` only in `rotrack/staging/backend/DATABASE_URL`; require exactly one `sslmode=verify-full`, exactly one matching `sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt`, the staging host, and no embedded password.
- [ ] Build the disposable Supabase CLI workdir exactly as documented, verify its copied files match `database/migrations/`, and link it only after the identity gate. Run the documented `migration list`, `db push --dry-run`, review ordered `001` then `002`, apply, list again, and remove the workdir.
- [ ] Run migrated-schema verification against staging with the explicit isolated-target acknowledgement:

  ```bash
  cd backend
  ROTRACK_TEST_DATABASE_ISOLATED=true \
  ROTRACK_TEST_DATABASE_MODE=verify \
  ROTRACK_TEST_DATABASE_URL='<staging-jdbc-url-with-verify-full-and-official-ca>' \
  ROTRACK_TEST_DATABASE_USERNAME='<authorized-staging-verification-role>' \
  ROTRACK_TEST_DATABASE_PASSWORD='<read-from-private-shell>' \
    mvn -Drotrack.postgres.integration=true \
    -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest' test
  ```

  Do not paste the populated command or shell history into evidence. Record only PostgreSQL major version, test counts, migration versions, and pass/fail.

### Runtime role and RLS boundaries

- [ ] Apply `deploy/staging/templates/runtime-role.sql.template` as an authorized staging administrator and set `rotrack_runtime`'s password with interactive `\password`.
- [ ] Open a separate connection authenticated as `rotrack_runtime`, run `runtime-role-audit.sql.template`, and confirm every boolean is true: dedicated login, non-superuser, `NOINHERIT`, no role memberships, `BYPASSRLS`, no create-db/create-role/replication/schema-create, only required `time_entries` SELECT/INSERT/UPDATE, no DELETE/TRUNCATE, no unrelated public table/sequence access, no direct database CREATE/TEMP grant, and no effective public routine execution outside the allowlisted security-definer signup trigger. Separately review provider-managed PUBLIC database privileges.
- [ ] Keep `BYPASSRLS`: pooled Spring JDBC cannot set `auth.uid()` from each browser token. This makes Spring's JWT subject and ownership-scoped queries the runtime authorization boundary; never grant schema administration or accept a client `user_id`.
- [ ] Independently confirm RLS is enabled on `public.users` and `public.time_entries`, and the seven checked-in ownership policies are present. The Data API must use anon/authenticated identities, never `rotrack_runtime`.
- [ ] Create two disposable staging users through Supabase Auth. Confirm the signup trigger creates matching `public.users` profiles without recording emails or UUIDs.
- [ ] Repeat the two-user Data API matrix: each user can list only owned rows; foreign filters return zero; forged `user_id` inserts return `403`; User B cannot read/update User A. Pass bearer tokens through private shell variables and discard them after the run.
- [ ] Repeat the Spring two-user ownership matrix for Work and Rot; cross-user active, stop, and dashboard access must remain empty/`404` as contracted.

### Connection budget

- [ ] Obtain the staging database and pooler caps from the provider dashboard. Select the lower safe application limit after provider-reserved connections.
- [ ] Set `ROTRACK_STAGING_DATABASE_CONNECTION_LIMIT`, `ROTRACK_STAGING_DATABASE_RESERVED_CONNECTIONS`, `ROTRACK_STAGING_DATABASE_MAXIMUM_POOL_SIZE`, desired tasks, and maximum tasks.
- [ ] Run deployment validation and record the arithmetic. Default task configuration is bounded (`maximumPoolSize=5`, `minimumIdle=0`, acquisition 5000 ms, validation 2000 ms); these are not permission to exceed the provider cap.
- [ ] Reserve connections for migrations/administration and budget for two task generations at `maximumPercent=200` during rollout. Register and read back the Application Auto Scaling maximum with the runbook commands; revalidate before any scaling or deployment-percentage change.

## 3. ECS/Fargate backend

- [ ] Verify the artifact by immutable digest, not a tag. Confirm non-root user, read-only root filesystem compatibility, Java 21 runtime, `wget`, port 8080, task-local CA path, and no source, `.env`, token, private key, or browser-auth artifact.
- [ ] Create/update only the six exact staging secret names in `deploy/staging/README.md`; restrict `secretsmanager:GetSecretValue` to the staging execution role and those resources.
- [ ] Run `./deploy/staging/aws-preflight.sh`: require distinct task/execution roles, empty attached/inline policies for the task role, caller-account separation, six required secret-access simulations, and the staging service tag. Separately review that the execution role's image/log actions and `secretsmanager:GetSecretValue` resources are limited to the expected staging resources. Confirm logs use `/rotrack/staging/backend`, staging retention, encryption, and redaction.
- [ ] Render and validate the task definition. Confirm its image equals the reviewed digest.
- [ ] Use private subnets, no public task IP, ALB-only ingress on container port 8080, TLS listener, outbound TLS limited to required services, and staging-only DNS/certificates.
- [ ] Container liveness uses unauthenticated `GET /api/v1/health`; ALB readiness uses unauthenticated `GET /api/v1/readiness`. Keep the 60-second startup grace and deployment circuit breaker/rollback.
- [ ] Register the task and update the staging service with the exact runbook commands. Wait for service stability and verify desired/running task counts without recording account/cluster/task identifiers.

## 4. Vercel frontend

- [ ] Confirm a dedicated Vercel project/team selection. Validate distinct staging/production team slugs, project names, organization IDs, and project IDs; after `vercel link`, require `.vercel/project.json` to match the protected staging IDs before any environment mutation or `--prod` deployment. Its stable staging origin must differ from development and production.
- [ ] Configure only `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_KEY`, and `NEXT_PUBLIC_API_URL` in the dedicated project's Vercel production target. The Supabase key must be anon/publishable, never service-role; API URL includes `/api/v1`.
- [ ] Disable unapproved preview access to the staging API. `CORS_ALLOWED_ORIGINS` contains the one stable HTTPS staging frontend origin, with no wildcard, paths, localhost, development, production, or comma-added preview origins.
- [ ] Build and deploy with the exact runbook commands. Confirm browser assets refer only to staging Auth/API endpoints using devtools without copying values to evidence.

## 5. Exact unauthenticated smoke commands

Set origins in a private shell. The API variable is the origin only (no `/api/v1`); never save populated shell output.

```bash
export ROTRACK_STAGING_API_ORIGIN='<staging-api-origin>'
export ROTRACK_STAGING_FRONTEND_ORIGIN='<staging-frontend-origin>'

curl --silent --show-error --fail-with-body \
  "$ROTRACK_STAGING_API_ORIGIN/api/v1/health" \
  | jq -e '. == {"status":"ok"}'

curl --silent --show-error --fail-with-body \
  "$ROTRACK_STAGING_API_ORIGIN/api/v1/readiness" \
  | jq -e '. == {"status":"ready"}'

curl --silent --show-error --fail-with-body \
  "$ROTRACK_STAGING_FRONTEND_ORIGIN/" >/dev/null

allowed_cors_status="$(curl --silent --show-error --dump-header /tmp/rotrack-staging-cors-allowed.headers \
  --output /dev/null --write-out '%{http_code}' --request OPTIONS \
  --header "Origin: $ROTRACK_STAGING_FRONTEND_ORIGIN" \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type' \
  "$ROTRACK_STAGING_API_ORIGIN/api/v1/time-entries/start")"
[[ "$allowed_cors_status" == 200 ]]
tr -d '\r' </tmp/rotrack-staging-cors-allowed.headers \
  | grep -Fxi "Access-Control-Allow-Origin: $ROTRACK_STAGING_FRONTEND_ORIGIN"

curl --silent --show-error --dump-header /tmp/rotrack-staging-cors-denied.headers \
  --output /dev/null --request OPTIONS \
  --header 'Origin: https://denied.invalid' \
  --header 'Access-Control-Request-Method: POST' \
  "$ROTRACK_STAGING_API_ORIGIN/api/v1/time-entries/start"

if grep -qi '^Access-Control-Allow-Origin:' /tmp/rotrack-staging-cors-denied.headers; then
  echo 'denied origin unexpectedly received CORS permission' >&2
  exit 1
fi
rm -f /tmp/rotrack-staging-cors-allowed.headers /tmp/rotrack-staging-cors-denied.headers
```

Then run the authenticated disposable-user critical path with local, external auth-state files that are never inspected or committed:

```bash
cd frontend
ROTRACK_E2E_BASE_URL="$ROTRACK_STAGING_FRONTEND_ORIGIN" \
ROTRACK_E2E_EXPECTED_API_URL="$ROTRACK_STAGING_API_ORIGIN/api/v1" \
ROTRACK_E2E_REQUIRE_AUTH=1 \
ROTRACK_E2E_USER_A_STORAGE_STATE='<external-user-a-state-path>' \
ROTRACK_E2E_USER_B_STORAGE_STATE='<external-user-b-state-path>' \
  npm run e2e
```

Record test counts only. Confirm Work/Rot start, navigation/reload, close/reopen restoration, explicit stop, dashboard deltas, and two-user isolation. Do not claim health, readiness, CORS, Auth, or browser evidence until these commands are actually observed against staging.

## 6. Failure and rollback boundaries

- [ ] A migration dry run with unexpected versions, identity mismatch, TLS failure, grant/RLS failure, connection-budget failure, unresolved sentinel, mutable image, wildcard CORS, unhealthy readiness, or cross-user access is a deployment blocker.
- [ ] Migrations are database-first and must remain backward-compatible. Application rollback selects the prior known-good image **digest** and Vercel deployment; it does not automatically reverse migrations.
- [ ] ECS circuit-breaker rollback may restore the prior task revision. Confirm the prior revision uses staging secrets and the same compatible schema before choosing it.
- [ ] Never roll back migration files by editing migration history or applying destructive SQL. Escalate schema rollback separately with backup/restore and data-loss review.

## 7. Teardown

Execute only for the authorized staging resources after preserving required redacted evidence:

```bash
aws ecs update-service \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cluster "$ROTRACK_STAGING_ECS_CLUSTER_NAME" \
  --service "$ROTRACK_STAGING_ECS_SERVICE_NAME" \
  --desired-count 0
aws ecs wait services-stable \
  --region "$ROTRACK_STAGING_AWS_REGION" \
  --cluster "$ROTRACK_STAGING_ECS_CLUSTER_NAME" \
  --services "$ROTRACK_STAGING_ECS_SERVICE_NAME"

vercel project rm '<dedicated-staging-project-name>' --scope '<staging-team-slug>' --yes
supabase projects delete '<staging-project-ref>'
rm -rf deploy/staging/rendered
```

- [ ] First rerun the three-ref identity gate and verify every destructive target says staging.
- [ ] Stop/delete only the staging ECS service/resources through their owning stack or approved AWS procedure; deregister staging task revisions and schedule staging secrets for deletion under retention policy.
- [ ] Remove staging DNS/certificates/log groups only after retention and incident requirements are met.
- [ ] Delete the dedicated Vercel staging project and Supabase staging project; never substitute a development or production identifier.
- [ ] Revoke disposable users/tokens, remove local Auth state files and CA copies, unset private shell variables, and remove ignored renders.
- [ ] Confirm no running staging tasks, routes, billable project, active secret access, or orphaned disposable data remains. Record yes/no outcomes without identifiers.
