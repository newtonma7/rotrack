# Startup, configuration, and health probes

This runbook defines the M2.2 local and ECS-facing configuration contract. Use only redacted values in logs and deployment evidence.

## Configuration checklist

### Frontend (build and runtime environment)

Next.js embeds these public values into browser assets. Configure them before `npm run dev` or `npm run build`:

| Name | Purpose |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL used by the Auth client |
| `NEXT_PUBLIC_SUPABASE_KEY` | Supabase anon/publishable browser key; never a service-role key |
| `NEXT_PUBLIC_API_URL` | Spring API base URL including `/api/v1` |

### Backend (runtime environment)

Spring receives these through the process environment (shell, IDE, container, or secrets manager). It does not load `backend/.env` itself.

| Name | Required | Purpose |
|---|---:|---|
| `DATABASE_URL` | yes | PostgreSQL JDBC URL; managed PostgreSQL must specify `sslmode=verify-full` and an explicit `sslrootcert` CA path |
| `DATABASE_USERNAME` | yes | Dedicated Spring application role |
| `DATABASE_PASSWORD` | yes | Application-role password; inject as a secret |
| `DATABASE_CONNECTION_TIMEOUT_MS` | no | Pool acquisition bound; defaults to 5000 ms |
| `DATABASE_POOL_VALIDATION_TIMEOUT_MS` | no | Hikari connection-validation bound; defaults to 2000 ms |
| `DATABASE_MAXIMUM_POOL_SIZE` | no | Per-task pool cap; defaults to 5 |
| `DATABASE_MINIMUM_IDLE` | no | Per-task idle-connection floor; defaults to 0 |
| `READINESS_CACHE_TTL` | no | Single-task readiness result cache; defaults to 5 seconds |
| `SUPABASE_ISSUER_URI` | yes | Exact Supabase token issuer (`.../auth/v1`) |
| `SUPABASE_JWKS_URI` | yes | Supabase asymmetric signing-key set (`.../.well-known/jwks.json`) |
| `SUPABASE_JWT_AUDIENCE` | no | Expected access-token audience; defaults to `authenticated` |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated exact HTTP(S) frontend origins; defaults to local port 3000 |
| `ROTRACK_MUTATION_RATE_LIMIT_REQUESTS` | no | Per-user mutation budget per fixed window; defaults to 30 |
| `ROTRACK_MUTATION_RATE_LIMIT_WINDOW` | no | Mutation window from 1 second through 1 hour; defaults to 1 minute |
| `ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS` | no | Bounded process-local user-key capacity; defaults to 10,000 |
| `ROTRACK_STRUCTURED_LOGGING_ENABLED` | no | Enables the reviewed request-completion logger; defaults to `false` locally |
| `ROTRACK_LOGGING_ENVIRONMENT` | when logging enabled | Exactly `staging` or `production` |
| `ROTRACK_SERVICE_VERSION` | when logging enabled | Non-placeholder immutable release identifier |
| `PORT` | no | HTTP port; defaults to 8080 |

The resource server accepts ES256 tokens only and validates issuer, audience, time claims, and UUID subject. CORS uses exact origins with credentials; do not configure `*` or include URL paths. Never place database credentials, bearer tokens, or service-role keys in frontend variables.

The process-local mutation limiter is defense in depth for authenticated start/stop requests; it does not replace the required trusted fleet-wide/authentication-adjacent edge control. Structured request logging is disabled for local development. Staging and production must enable it explicitly with validated environment/release metadata and must still prove collector-side redaction and routing.

`DATABASE_URL` is the only TLS-mode source because PostgreSQL JDBC URL parameters override separate driver properties. Managed PostgreSQL must include `sslmode=verify-full`, which verifies encryption, the certificate chain, and the database hostname; startup rejects weaker or missing modes. Download the official CA certificate from the database provider, store it in the deployment secret/configuration system, mount it read-only (the examples use `/run/secrets/supabase-db-ca.crt`), and set that path with `sslrootcert`. Do not capture and trust a certificate from an unverified connection. PostgreSQL JDBC 42.7.x treats `sslrootcert=system` as a literal filename, so it is not a portable trust-store setting. Only the explicit `local` Spring profile may use `sslmode=disable` for loopback PostgreSQL.

## Deterministic local startup

From a clean clone, apply migrations first and create populated ignored environment files from both templates. Select the pinned Node and Java 21 toolchains, then use two terminals.

```bash
# terminal 1
set -a
source backend/.env
set +a
cd backend
mvn spring-boot:run
```

```bash
# terminal 2
cd frontend
npm ci
npm run dev
```

Expected URLs:

- Frontend: `http://localhost:3000`
- API base: `http://localhost:8080/api/v1`
- Liveness: `http://localhost:8080/api/v1/health`
- Readiness: `http://localhost:8080/api/v1/readiness`

Required backend settings deliberately have no credential defaults. Missing settings or an unavailable/unmigrated database prevent a misleading successful application startup.

## Health contract

| Probe | Dependency calls | Success | Failure |
|---|---|---|---|
| `GET /api/v1/health` | none | `200 {"status":"ok"}` | process cannot serve HTTP |
| `GET /api/v1/readiness` | obtains and validates a pooled database connection | `200 {"status":"ready"}` | `503 {"status":"not_ready"}` |

Both endpoints are unauthenticated and expose only the stable `status` field. They never return a JDBC URL, SQL error, host, credential, JWT setting, or stack trace. The readiness check reflects PostgreSQL because all authenticated application-data requests require that persistence boundary. Results are cached per task for five seconds by default, so repeated public polls cause at most one pool checkout per cache interval. It does not actively call the JWKS endpoint: signing keys are fetched and cached by Spring Security, so coupling readiness to a fresh external fetch would incorrectly remove an otherwise capable task from service.

For ECS/Fargate:

- use `/api/v1/health` as the container liveness command;
- use `/api/v1/readiness` as the load-balancer target health path and restrict direct Internet routing to health paths with listener/security-group policy where the platform permits;
- configure health intervals/timeouts around the bounded connection/validation timeouts, with a startup grace period and thresholds that avoid replacement churn during a shared database incident;
- do not route traffic to a task until readiness returns 200;
- budget PostgreSQL connections as `DATABASE_MAXIMUM_POOL_SIZE × maximum ECS tasks`, reserving capacity for migrations and operations.

An ALB marks targets unhealthy and ECS may replace repeatedly unhealthy tasks even when container liveness is still healthy. Before staging, choose thresholds and deployment circuit-breaker behavior that prevent a database-wide outage from causing an unbounded replacement/connection storm.

## Smoke probes

```bash
curl --silent --show-error --fail-with-body \
  http://localhost:8080/api/v1/health
curl --silent --show-error --fail-with-body \
  http://localhost:8080/api/v1/readiness
```

A CORS preflight from the configured frontend origin should return that exact origin, while an unconfigured origin must not receive `Access-Control-Allow-Origin`:

```bash
curl --include --request OPTIONS \
  --header 'Origin: http://localhost:3000' \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type' \
  http://localhost:8080/api/v1/time-entries/start
```

If readiness is 503, inspect server-side logs and database reachability without copying credentials or connection strings into evidence. If liveness is 200 at the same time, do not restart-loop the process; keep it out of traffic until the dependency recovers.
