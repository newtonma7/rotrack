# Startup, configuration, and health probes

This runbook defines the M2.2 local and Azure Container Apps-facing configuration contract. The authorized non-production Azure/Vercel deployment passed configuration readback plus HTTPS health/readiness on 2026-08-09; see [`azure-nonproduction.md`](azure-nonproduction.md). Public GitHub visibility, `main` protection, exact required contexts, `nonproduction` protected-main policy, empty auth-secret inventories, absent/default-disabled authenticated-E2E variable, and public-repo security features are read back. PR #18 supplied hosted-green protected-path evidence and PR #19 supplied deliberate-red required-check blocking evidence; M3.1 is Verified. Authenticated smoke, alert routing, rollback, and production remain unverified. Use only redacted values in logs and deployment evidence.

## Configuration checklist

### Frontend (build and runtime environment)

Next.js embeds these public values into browser assets. Configure them before `npm run dev` or `npm run build`:

| Name | Purpose |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL used by the Auth client |
| `NEXT_PUBLIC_SUPABASE_KEY` | Supabase anon/publishable browser key; never a service-role key |
| `NEXT_PUBLIC_API_URL` | Spring API base URL including `/api/v1`; required for production builds |
| `NEXT_PUBLIC_SITE_URL` | Canonical public site origin used for metadata, sitemap, and robots; required for production builds (local default: `http://localhost:3000`) |

### Backend (runtime environment)

Spring receives these through the process environment (shell, IDE, container, or secrets manager). It does not load `backend/.env` itself.

| Name | Required | Purpose |
|---|---:|---|
| `DATABASE_URL` | yes | PostgreSQL JDBC URL; managed PostgreSQL must specify `sslmode=verify-full` and an explicit `sslrootcert` CA path |
| `DATABASE_USERNAME` | yes | Dedicated Spring application role |
| `DATABASE_PASSWORD` | yes | Application-role password; inject as a secret |
| `DATABASE_CONNECTION_TIMEOUT_MS` | no | Pool acquisition bound; defaults to 5000 ms |
| `DATABASE_POOL_VALIDATION_TIMEOUT_MS` | no | Hikari connection-validation bound; defaults to 2000 ms |
| `DATABASE_MAXIMUM_POOL_SIZE` | no | Per-process/per-replica pool cap; defaults to 5 |
| `DATABASE_MINIMUM_IDLE` | no | Per-process/per-replica idle-connection floor; defaults to 0 |
| `READINESS_CACHE_TTL` | no | Per-process readiness result cache; defaults to 5 seconds |
| `SUPABASE_ISSUER_URI` | yes | Exact Supabase token issuer (`.../auth/v1`) |
| `SUPABASE_JWKS_URI` | yes | Supabase asymmetric signing-key set (`.../.well-known/jwks.json`) |
| `SUPABASE_JWT_AUDIENCE` | no | Expected access-token audience; defaults to `authenticated` |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated exact HTTP(S) frontend origins; defaults to local port 3000 |
| `ROTRACK_MUTATION_RATE_LIMIT_REQUESTS` | no | Per-user mutation budget per fixed window; defaults to 30 |
| `ROTRACK_MUTATION_RATE_LIMIT_WINDOW` | no | Mutation window from 1 second through 1 hour; defaults to 1 minute |
| `ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS` | no | Bounded process-local user-key capacity; defaults to 10,000 |
| `ROTRACK_NOTES_WRITES_ENABLED` | no | Enables Notes mutations; defaults to `true`, and enabled deployments fail closed without the HMAC secret |
| `ROTRACK_NOTES_HMAC_SECRET` | when Notes writes enabled | Stable runtime-only HMAC key of at least 32 UTF-8 bytes; inject through the runtime secret store and never log or rotate casually |
| `ROTRACK_STRUCTURED_LOGGING_ENABLED` | no | Enables the reviewed request-completion logger; defaults to `false` locally |
| `ROTRACK_LOGGING_ENVIRONMENT` | when logging enabled | Exactly `staging` or `production` |
| `ROTRACK_SERVICE_VERSION` | when logging enabled | Non-placeholder immutable release identifier |
| `PORT` | no | HTTP port; defaults to 8080 |

The resource server accepts ES256 tokens only and validates issuer, audience, time claims, and UUID subject. CORS uses exact origins with credentials; do not configure `*` or include URL paths. Never place database credentials, bearer tokens, service-role keys, or the Notes HMAC secret in frontend variables. Keep the Notes HMAC key stable across ordinary revisions because rotation changes idempotency fingerprints; a future rotation requires an explicit compatibility plan.

The process-local mutation limiter is defense in depth for authenticated start/stop requests; it does not replace the required trusted fleet-wide/authentication-adjacent edge control. Structured request logging is disabled for local development. Staging and production must enable it explicitly with validated environment/release metadata and must still prove collector-side redaction and routing.

`DATABASE_URL` is the only TLS-mode source because PostgreSQL JDBC URL parameters override separate driver properties. Managed PostgreSQL must include `sslmode=verify-full`, which verifies encryption, the certificate chain, and the database hostname; startup rejects weaker or missing modes. Download the official CA certificate from the database provider and store it in the deployment secret/configuration system. The container entrypoint requires `DATABASE_CA_CERTIFICATE_PATH=/tmp/rotrack-certs/supabase-db-ca.crt`, materializes the injected CA there with mode `0600`, and requires the URL's `sslrootcert` to match exactly. A direct local process may use another readable path, but the checked-in example uses the container-compatible path to avoid configuration drift. Do not capture and trust a certificate from an unverified connection. PostgreSQL JDBC 42.7.x treats `sslrootcert=system` as a literal filename, so it is not a portable trust-store setting. Only the explicit `local` Spring profile may use `sslmode=disable` for loopback PostgreSQL.

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

Both endpoints are unauthenticated and expose only the stable `status` field. They never return a JDBC URL, SQL error, host, credential, JWT setting, or stack trace. The readiness check reflects PostgreSQL because all authenticated application-data requests require that persistence boundary. Results are cached per process/replica for five seconds by default, so repeated public polls cause at most one pool checkout per cache interval. It does not actively call the JWKS endpoint: signing keys are fetched and cached by Spring Security, so coupling readiness to a fresh external fetch would incorrectly remove an otherwise capable replica from service.

For Azure Container Apps:

- use `/api/v1/health` as the process liveness probe on port `8080`;
- use `/api/v1/readiness` as the database-aware readiness probe and route traffic only to ready replicas;
- configure probe intervals/timeouts around the bounded connection/validation timeouts, with startup grace and thresholds that avoid replacement churn during a shared database incident;
- budget PostgreSQL connections as `DATABASE_MAXIMUM_POOL_SIZE × maximum replicas`, including rollout overlap and reserving capacity for migrations and operations;
- keep separate target Azure managed environments, resource groups, and Container Apps for non-production and production. The approved target names are managed environments `rotrack-nonproduction-env` / `rotrack-production-env`, resource groups `rotrack-nonproduction` / `rotrack-production`, and apps `rotrack-api-nonproduction` / `rotrack-api-production`;
- The canonical shared-hosted path now uses Consumption `minReplicas=1` by product-owner decision after ten trials measured p95 readiness above 30 seconds. Observe actual billing and revisit only with an explicit decision. Ensure alerts distinguish any future cold start from sustained unavailability.

Container Apps can replace unhealthy replicas even when process liveness remains healthy. Before non-production deployment, choose thresholds and revision/traffic behavior that prevent a database-wide outage from causing an unbounded replacement or connection storm. Before production promotion, add Azure budget and credit-expiry alerts; these are required safeguards, not evidence that billing or alerts are configured.

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

If readiness is 503, inspect server-side logs and database reachability without copying credentials or connection strings into evidence. If liveness is 200 at the same time, do not restart-loop the Container App; keep the revision out of traffic until the dependency recovers. If the app has just scaled from zero, measure the expected cold-start window before treating the first probe failure as an incident.
