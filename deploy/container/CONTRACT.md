# Backend container contract

This directory defines the staging artifact boundary only. It does not provision or deploy remote infrastructure.

## Artifact and runtime

- Build from a clean repository root with `scripts/container/build-image.sh`; release-candidate builds refuse dirty source by default. `REQUIRE_CLEAN=0` is only for local artifact validation. The immutable release reference is `IMAGE_REPOSITORY@IMAGE_DIGEST`; a mutable tag alone is not a deployment input.
- The image is Linux/amd64, Java 21, port `8080`, UID/GID `10001:10001`, and targets reviewed Fargate platform `1.4.0`, and has OCI revision/version/created labels derived from the Git commit. The runtime layer contains only the JRE, packaged application, and entrypoint—not source, host `target`, frontend, VCS, environment, credential, or browser-test artifacts.
- ECS keeps the root filesystem read-only and mounts task-local writable storage only at `/tmp`. SIGTERM starts Spring graceful shutdown; ECS allows 30 seconds and Spring drains for at most 25 seconds.
- `/api/v1/health` is the process-only container liveness check. `/api/v1/readiness` is the database-aware ALB target check. Do not substitute one for the other.

## Runtime configuration

The task definition uses these exact environment names. Values below are contracts, not credentials.

Secrets Manager injection:

- `DATABASE_URL` — must use `sslmode=verify-full&sslrootcert=/tmp/rotrack-certs/supabase-db-ca.crt`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `DATABASE_CA_CERTIFICATE_PEM` — official managed database provider CA, injected as a secret and written mode `0600`; never a certificate captured from an unverified connection
- `SUPABASE_JWKS_URI`
- `SUPABASE_ISSUER_URI`

Non-secret task configuration:

- `PORT=8080`
- `DATABASE_CA_CERTIFICATE_PATH=/tmp/rotrack-certs/supabase-db-ca.crt`
- `DATABASE_CONNECTION_TIMEOUT_MS=5000`
- `DATABASE_POOL_VALIDATION_TIMEOUT_MS=2000`
- `DATABASE_MAXIMUM_POOL_SIZE=5`
- `DATABASE_MINIMUM_IDLE=0`
- `READINESS_CACHE_TTL=5s`
- `SUPABASE_JWT_AUDIENCE=authenticated`
- `CORS_ALLOWED_ORIGINS` — comma-separated exact staging HTTPS origins; no wildcard, path, credentials, development URL, or trailing slash
- `SERVER_SHUTDOWN=graceful`
- `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=25s`
- `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`
- `ROTRACK_STRUCTURED_LOGGING_ENABLED` — must be explicitly `true` for staging/production request logging
- `ROTRACK_LOGGING_ENVIRONMENT` — must be `staging` or `production`; staging sets `staging`
- `ROTRACK_SERVICE_VERSION` — non-secret immutable release ID; the staging contract binds it to the backend image digest
- `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=WARN`
- `LOGGING_LEVEL_ORG_HIBERNATE_SQL=OFF`, `LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=OFF`, and `SPRING_JPA_SHOW_SQL=false` — prevent SQL/bind payload logging

Size the database for `DATABASE_MAXIMUM_POOL_SIZE × maximum running tasks`, including deployment surge, and reserve connections for migrations and operations.

## TLS and log safety

The entrypoint materializes the managed CA in task-local `/tmp`, verifies that `DATABASE_URL` names the same path and `verify-full`, rejects custom TLS factories/hostname verifiers, unsets the PEM environment variable, and then `exec`s Java. It never prints a secret or URL. The task uses the ECS JSON console format and CloudWatch `awslogs`; Spring Security logging is WARN.

Do not enable request-header/body dumps, ECS Exec, JVM command-line secrets, or environment logging. Logs and deployment evidence must never contain bearer tokens, credentials, JDBC URLs, CA bodies, notes, or reflections. The source/templates implement and enable the application redaction boundary; collector ingestion/redaction, telemetry, alerts/routing, and observed staging evidence remain unverified. The staging task explicitly enables the reviewed structured request logger and fails validation unless its environment is `staging` and its service version is the lowercase 64-hex digest suffix of the immutable backend image URI.

## Base templates

`deploy/ecs/base/*.json` are placeholder-only AWS CLI input templates. Render into an ignored temporary directory, validate the rendered files, register the target group/task definition/service, and keep generated files out of Git. Required placeholders are uppercase `${...}` tokens; use an immutable `sha256:...` image digest. The task runs in private subnets without a public IP and expects an existing ALB listener, security groups, IAM roles, Secrets Manager entries, and CloudWatch log group from the staging operations lane.

The target group sends HTTP to the private task because public TLS terminates at the ALB. Public listener configuration must be HTTPS with an AWS-managed certificate and redirect HTTP to HTTPS. Security groups must allow task port 8080 only from the ALB security group. Private subnets must have DNS plus outbound connectivity through approved NAT or VPC endpoints for ECR image pulls, Secrets Manager, CloudWatch Logs, Supabase Auth/JWKS, and Supabase PostgreSQL; a task with no public IP is not deployable without those routes.

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

`build-image.sh` prints the local image ID and content digest. A registry digest exists only after a separately approved push; this lane does not publish or deploy.
