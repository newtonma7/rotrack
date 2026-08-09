# Backend container contract

This directory defines the platform-neutral backend artifact boundary. It does not provision or deploy remote infrastructure. The approved runtime integration is Azure Container Apps Consumption; the checked-in ECS/Fargate templates are historical, unselected AWS artifacts and have not been converted to Azure. The current Docker/Podman builder produces an OCI-compatible container image; strict registry manifest media type and immutable registry digest remain deployment-time checks.

## Artifact and runtime

- Build from a clean repository root with `scripts/container/build-image.sh`; release-candidate builds refuse dirty source by default. `REQUIRE_CLEAN=0` is only for local artifact validation. The immutable release reference is `IMAGE_REPOSITORY@IMAGE_DIGEST`; a mutable tag alone is not a deployment input.
- The image is a Linux/amd64 OCI-compatible image, Java 21, port `8080`, UID/GID `10001:10001`, and has OCI revision/version/created labels derived from the Git commit. The runtime layer contains only the JRE, packaged application, and entrypoint—not source, host `target`, frontend, VCS, environment, credential, or browser-test artifacts.
- The image declares no cloud-provider task, role, network, logging, or registry contract. If the Azure registry integration is selected, prefer Azure Container Registry with managed identity for Container Apps pulls; that choice does not alter the platform-neutral image.
- The image is locally validated as read-only-root compatible with writable space only at `/tmp`. ACA enforcement of a read-only root filesystem is not claimed by the current target integration. Until a supported ACA control is verified, require non-root execution, application writes limited to `/tmp`, no remote debug shell, least-privilege managed identity, and isolated secret injection. SIGTERM starts Spring graceful shutdown; the platform must allow 30 seconds and Spring drains for at most 25 seconds.
- `/api/v1/health` is the process-only liveness check. `/api/v1/readiness` is the database-aware readiness check. Do not substitute one for the other.

## Runtime configuration

The deployment integration uses these exact environment names. Values below are contracts, not credentials.

Secrets/configuration injection:

- `DATABASE_URL` — must use `sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `DATABASE_CA_CERTIFICATE_PEM` — official managed database provider CA, injected at runtime and written mode `0600`; never a certificate captured from an unverified connection
- `SUPABASE_JWKS_URI`
- `SUPABASE_ISSUER_URI`

Non-secret runtime configuration:

- `PORT=8080`
- `DATABASE_CA_CERTIFICATE_PATH=/tmp/rotrack-certs/supabase-db-ca.crt`
- `DATABASE_CONNECTION_TIMEOUT_MS=5000`
- `DATABASE_POOL_VALIDATION_TIMEOUT_MS=2000`
- `DATABASE_MAXIMUM_POOL_SIZE=5`
- `DATABASE_MINIMUM_IDLE=0`
- `READINESS_CACHE_TTL=5s`
- `SUPABASE_JWT_AUDIENCE=authenticated`
- `CORS_ALLOWED_ORIGINS` — comma-separated exact HTTPS origins for the selected Vercel environment; no wildcard, path, credentials, development URL, or trailing slash
- `SERVER_SHUTDOWN=graceful`
- `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=25s`
- `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` — Spring's `ecs` value means Elastic Common Schema (structured JSON), not AWS Elastic Container Service; it is vendor-neutral and does not select a cloud platform
- `ROTRACK_STRUCTURED_LOGGING_ENABLED` — must be explicitly `true` for staging/production request logging
- `ROTRACK_LOGGING_ENVIRONMENT` — must be `staging` or `production`; logical GitHub `nonproduction` maps to the staging label
- `ROTRACK_SERVICE_VERSION` — non-secret immutable release ID; the target ACA deployment/readback must bind it to the image digest. Application startup currently validates only that the value is non-placeholder, so equality is not yet an application safeguard
- `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=WARN`
- `LOGGING_LEVEL_ORG_HIBERNATE_SQL=OFF`, `LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=OFF`, and `SPRING_JPA_SHOW_SQL=false` — prevent SQL/bind payload logging

Size the database for `DATABASE_MAXIMUM_POOL_SIZE × maximum replicas`, including revision overlap, and reserve connections for migrations and operations. Scale-to-zero is initially accepted. Before production promotion, run at least 10 non-production wake-up trials; keep production minimum replicas at `0` only with explicit product-owner acceptance when p95 readiness is at most 30 seconds and no trial exceeds 60 seconds, otherwise set it to `1`. Azure budget alerts are notifications, not a hard spending cap; cost and credit-expiry data can be delayed.

## TLS, probes, and log safety

The entrypoint materializes the managed CA in container-local ephemeral `/tmp`, verifies that `DATABASE_URL` names the same path and `verify-full`, rejects custom TLS factories/hostname verifiers, unsets the PEM environment variable, and then `exec`s Java. It never prints a secret or URL. The Azure deployment integration must inject the CA and secrets through its approved secret/configuration boundary; no CA is baked into the image.

Do not enable request-header/body dumps, remote shell/debug access, JVM command-line secrets, or environment logging. Logs and deployment evidence must never contain bearer tokens, credentials, JDBC URLs, CA bodies, notes, or reflections. The source implements the application redaction boundary; collector ingestion/redaction, telemetry, alerts/routing, budget alerts, and observed deployment evidence remain unverified. The future ACA deployment/readback check must bind the non-secret service version to the immutable image digest; that binding currently exists only in the legacy AWS validator. GitHub/ACA `nonproduction` maps to runtime/telemetry label `staging`; production maps to `production`.

## Azure deployment boundary

The target has two concrete boundaries:

- Non-production: managed environment `rotrack-nonproduction-env` inside resource group `rotrack-nonproduction`, Container App `rotrack-api-nonproduction`, Azure Container Apps Consumption, Vercel Preview, target logical GitHub environment `nonproduction`, and the shared existing non-production/dev Supabase Free project.
- Production: managed environment `rotrack-production-env` inside resource group `rotrack-production`, Container App `rotrack-api-production`, Azure Container Apps Consumption, Vercel Production in the same Vercel project, target logical GitHub environment `production`, and the newly created `rotrack-prod` Supabase Free project.

These are target names and separation rules only. No resource, identity, managed identity, ACR repository, Vercel deployment, GitHub environment setting, Supabase setting, or alert is claimed as configured or verified. The Azure managed environment is the security boundary and must remain separate, in its matching resource group, from production/non-production peers. The Azure integration must use HTTPS ingress, port `8080`, liveness/readiness probes, graceful shutdown, exact CORS, non-root execution, writes limited to `/tmp`, no remote debug shell, least-privilege identity/secret boundaries, and a managed-identity pull if ACR is selected. Do not claim ACA read-only-root enforcement until a supported provider control is implemented and observed.

## Historical AWS artifacts

`deploy/ecs/base/*.json` and the AWS-oriented files under `deploy/staging/` remain checked-in legacy/unselected artifacts from the earlier ECS/Fargate design. They are not an active deployment path, have not been converted to Azure, and should not be used to infer current infrastructure. Their validators and scripts still enforce AWS-era assumptions; replacing them is residual work and is outside this documentation-only change.

## Verification

```sh
scripts/container/test-contract.sh
scripts/container/build-image.sh
IMAGE_REF=rotrack-api:<git-sha> scripts/container/test-image.sh
# Only with a safe migrated non-production fixture and an env file outside the repository:
IMAGE_REF=rotrack-api:<git-sha> \
ROTRACK_CONTAINER_NETWORK=<isolated-container-network> \
  scripts/container/smoke-image.sh /absolute/path/to/runtime.env
```

`build-image.sh` prints the local image ID and content digest. A registry digest and manifest media type exist only after a separately approved push/readback; this lane does not publish or deploy. Any AWS/ECS/Fargate contract checks that remain in the legacy scripts are historical validation and are not Azure deployment verification.
