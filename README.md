# rotrack

rotrack is a personal study and productivity tracker. The MVP records explicit `WORK` and `ROT` sessions, authenticates with Supabase, and displays a private seven-day dashboard.

## Source of truth

Read these before changing the repository:

- [`arch.plan.md`](arch.plan.md) — product invariants, architecture, security boundaries, and API/data contracts.
- [`todo.md`](todo.md) — dependency order, acceptance criteria, status, and verification evidence.
- [`frontend/DESIGN.md`](frontend/DESIGN.md) — frontend visual and interaction design.
- [`AGENTS.md`](AGENTS.md) — repository-specific development instructions.
- [`docs/operations/startup-and-health.md`](docs/operations/startup-and-health.md) — deployment configuration and health-probe contract.

The root README is a setup guide, not a substitute for those documents. Source code and recorded command output outrank stale documentation.

## Repository layout

```text
frontend/                 Next.js App Router application
backend/                  Spring Boot REST API
database/migrations/      Ordered Supabase PostgreSQL migrations
arch.plan.md              Architecture and contracts
todo.md                   Delivery backlog and evidence
```

The runtime data flow is:

```text
Browser → Supabase Auth
Browser → Spring Boot API with a Supabase bearer JWT
Spring Boot API → Supabase PostgreSQL
```

The frontend uses Supabase for authentication only. Application data goes through the API client in `frontend/src/lib/api.ts`.

## Prerequisites

- Node.js and npm
- Java 21, as selected by `.java-version`
- Maven 3.9+
- Access to the existing non-production/dev Supabase Free project for local development and approved environment-scoped authenticated E2E; credential-free PR CI uses isolated disposable PostgreSQL instead of hosted Supabase
- A shell or IDE that can provide environment variables to Spring Boot

The repository pins Node with `.nvmrc` and Java with `.java-version`. Activate those versions with your version manager before running the commands below.

## First-time setup

### 1. Apply the database migration

Apply the SQL files in [`database/migrations/`](database/migrations/) to a development Supabase project using the Supabase CLI or SQL editor. Start with `001_initial_schema.sql` and apply migrations in filename order.

The backend uses `spring.jpa.hibernate.ddl-auto=validate`; it validates an existing schema and does not create or update tables for you.

### 2. Configure the frontend

```bash
cp frontend/.env.example frontend/.env.local
```

Set these values in `frontend/.env.local`:

```text
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
NEXT_PUBLIC_SUPABASE_KEY=your-anon-key
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

`NEXT_PUBLIC_*` values are embedded in browser code. They may contain public URLs/keys, but must not contain database passwords or other secrets.

### 3. Configure the backend

```bash
cp backend/.env.example backend/.env
```

Fill in the values from your Supabase project. Spring Boot does not automatically read `.env` files. Before starting it, export the variables in your shell or configure them in your IDE/container. For a shell session:

```bash
set -a
source backend/.env
set +a
```

The backend variables are documented in [`backend/.env.example`](backend/.env.example): database connection/TLS, pool limits/timeouts, Supabase issuer/JWKS URLs and JWT audience, CORS origins, readiness caching, mutation-rate limits, structured-request-log metadata, and port. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `SUPABASE_JWKS_URI`, and `SUPABASE_ISSUER_URI` are required; startup fails rather than silently using development credentials. `DATABASE_URL` is the only TLS-mode source and managed PostgreSQL must include `sslmode=verify-full` plus an explicit `sslrootcert` path to the provider's official CA certificate. The example uses the container path `/tmp/rotrack-certs/supabase-db-ca.crt`. For a direct local JVM process, replace only that URL parameter with the absolute path where you stored the official CA, for example `/home/YOUR_USER/.config/rotrack/supabase-db-ca.crt`; do not use a literal `$HOME` inside the quoted JDBC URL. Only the explicit `local` Spring profile may use `sslmode=disable` for loopback PostgreSQL.

Never commit populated `.env` files or include their values in issue/PR evidence.

## Run locally

Start the backend in one terminal:

```bash
cd backend
set -a && source .env && set +a
mvn spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open <http://localhost:3000>. The API listens on <http://localhost:8080/api/v1/health> by default. `CORS_ALLOWED_ORIGINS` must exactly match the browser origin. In the 2026-08-09 local recheck, port 3001 received HTTP 200 with exact allow-origin while port 3000 received HTTP 403 with no allow-origin. To use port 3000, configure `CORS_ALLOWED_ORIGINS` accordingly; otherwise start with `npm run dev -- -p 3001` and open <http://localhost:3001>. A frontend page loading does not by itself prove that credentialed API CORS succeeds. Probe the API with:

```bash
curl --fail-with-body http://localhost:8080/api/v1/health
curl --fail-with-body http://localhost:8080/api/v1/readiness
```

Liveness always performs no dependency calls and returns `200 {"status":"ok"}` while the process can serve HTTP. Readiness validates a database connection and returns either `200 {"status":"ready"}` or `503 {"status":"not_ready"}`. Both endpoints are unauthenticated and intentionally omit dependency details. Use liveness for Container App restarts and readiness for traffic eligibility; see the [operations runbook](docs/operations/startup-and-health.md).

## Validation commands

Run frontend commands from `frontend/`:

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

Run backend commands from `backend/`:

```bash
mvn test
mvn package
```

The repository has focused Vitest and JUnit suites for the timer, API/security boundaries, dashboard semantics, preferences, completed history/manual corrections, mutation rate limiting, and structured-log redaction. Opt-in PostgreSQL migration/repository checks and authenticated browser coverage require explicit isolated targets and external auth state; their latest evidence is recorded in [`todo.md`](todo.md). Do not report tests, typechecking, builds, migrations, or browser flows as passing unless they were actually run. Behavior changes must add tests as part of the same change.

## Current API surface

All paths use the `/api/v1` prefix. Authenticated endpoints require `Authorization: Bearer <supabase-jwt>`.

- `GET /health` — unauthenticated, database-independent liveness check
- `GET /readiness` — unauthenticated database readiness check (`200` ready, `503` not ready)
- `POST /time-entries/start` — start a `WORK` or `ROT` session (`201 Created`)
- `GET /time-entries/active` — get the authenticated user's active session
- `PUT /time-entries/{id}/stop` — stop an owned session by ID
- `GET /time-entries/history?cursor=...` — list up to 20 owned completed entries; pass the opaque cursor back unchanged
- `POST /time-entries`, `PUT /time-entries/{id}`, `DELETE /time-entries/{id}` — create, edit, or delete completed entries
- `GET /preferences`, `PUT /preferences` — read or update owned timezone, goal, and private sharing defaults
- `GET /notes`, `GET /notes/{id}`, `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}` — list, read, create, update, or hard-delete owned private Notes
- `GET /dashboard/stats?timeZone=Area%2FCity` — get timestamp-derived totals and daily buckets; optional paired `start`/`end` ISO local dates define a half-open range

M4 contracts are documented in [`docs/specs/m4-contracts.md`](docs/specs/m4-contracts.md), and Notes/rich-text contracts in [`docs/specs/m5-contracts.md`](docs/specs/m5-contracts.md). The `/settings`, `/history`, `/notes`, and `/tracker` application routes are protected. History shows completed entries only; active tracking remains on `/tracker`. The saved IANA timezone controls dashboard/history calendar rendering and history form conversion, with the browser timezone used only while no timezone is saved. Changing it never rewrites stored UTC instants.

The backend derives the acting user from the validated JWT subject. Clients must not send a `user_id`. Dashboard and history durations are timestamp-derived seconds; clients never submit authoritative duration.

## Working conventions

- Read `arch.plan.md` before changing timer lifecycle, authentication, authorization, migrations, timezones, or privacy behavior.
- Tracking is explicit: sessions remain active across browser lifecycle events until explicitly stopped, and the server's timestamps are authoritative.
- Preserve the two activity buckets: `WORK` and `ROT`.
- Keep frontend App Router code under `frontend/src/app`, feature components under `frontend/src/components`, hooks under `frontend/src/hooks`, API/auth code under `frontend/src/lib`, and types under `frontend/src/types`.
- Read `frontend/DESIGN.md` before UI work. Preserve the Tangerine Studio tokens, typography, accessibility, restrained motion, and no-stock-imagery rule.
- Keep backend layers under `com.rotrack`: controllers, services, repositories, models, DTOs, configuration, and exceptions.
- Scope every backend read and mutation to the authenticated user. Never log tokens, credentials, private notes, or reflections.

## Known limitations

The repository is still working toward the production-ready MVP. The dated 2026-08-11 hosted validation below is sanitized; full release identifiers remain in private evidence. In particular:

- Empty-database migration application, migrated-database repository checks, the dedicated runtime-role audit, and direct Data API RLS evidence pass against isolated/development targets. On 2026-08-09 the product owner/operator attested that the already-confirmed fresh disposable user completed first sign-in and reached `/dashboard`; this closes the manual first-sign-in acceptance step but is distinct from automated deployed authenticated E2E.
- The authenticated two-user Spring ownership matrix and required-authenticated Playwright 4/4 flow are recorded as passing. Local authenticated M4 acceptance covers preference defaults/persistence/isolation and history pagination, create/edit/delete, overlap rejection, active-entry exclusion, ownership isolation, and mobile overflow; the authorized hosted M4 rollout also passed, so M4 is **Verified**. M5.1 Notes data/API is Verified locally and M5.2 editor/workspace is **Implemented—unverified**; its dated shared-hosted rollout authorization does not become verification until protected CI, migration 006, immutable deployment, and hosted acceptance evidence pass.
- Managed-CA startup, independent liveness/readiness, degraded readiness `503`, and exact CORS behavior are locally verified. On 2026-08-11, source commit `744635c` passed the focused Azure contract/readback, publish, preflight, RBAC, container, and release checks. The reviewed backend candidate was deployed to the canonical shared ACA implementation boundary; digest/service-version equality, the production runtime label, scale `1..1`, 100% traffic, readiness/readback, and the canonical Vercel Production alias all passed. Public smoke passed for frontend `200`, exact liveness/readiness `200` contracts, HTTP-to-HTTPS redirect, allowed CORS, and denied unrelated CORS. Hosted authenticated smoke passed `4/4` with zero skipped, unexpected, or flaky results and API-target binding. The corrected no-schema-change backend/frontend rollback rehearsal passed and ended with the candidate restored. Rate limiting remains an accepted blocker; ten cold-start trials, collector redaction, alert delivery/receipt, and alert routing evidence remain open. The Azure action-group provider test-notification command returned failure; synthetic alert delivery is **NOT VERIFIED**, and no successful delivery or receipt is claimed. Retained operator-owned synthetic accounts and stopped rows are not claimed as cleaned up.
- The long-term target architecture remains two Supabase Free projects, one Vercel project using Preview and Production, logical GitHub `nonproduction`/`production` environments, and separate Azure boundaries. The current product-owner override is explicit: canonical hosted production uses the shared Supabase project, Vercel Production, and the existing ACA implementation boundary with the `production` runtime label until quota permits separation. The reserved separate production project/boundary is not the current path. This does not waive release gates or authorize use of real private data. The repository is public and `main` is protected under the approved solo-maintainer policy: pull requests, zero human approvals, strict app-bound required checks, administrator enforcement, linear history, no force pushes/deletion, and advisory `CODEOWNERS`. Required checks are `Guards and secret scan`, `Frontend`, `Backend`, `Backend container artifact`, `PostgreSQL migrations`, `Analyze (actions)`, `Analyze (java-kotlin)`, and `Analyze (javascript-typescript)`. Repository, `nonproduction`, and `production` authentication secret inventories are empty; `ROTRACK_AUTHENTICATED_E2E_ENABLED` is absent/default-disabled. Vulnerability reporting, Dependabot security fixes, secret scanning, and push protection are enabled. See [`docs/operations/azure-nonproduction.md`](docs/operations/azure-nonproduction.md) and [`docs/operations/single-environment.md`](docs/operations/single-environment.md).
- Supabase Free projects may auto-pause after seven days of low activity. Free does not include the Pro/Team/Enterprise automatic daily backup feature or PITR; plan encrypted access-controlled off-site `supabase db dump` exports, retention, and a restore rehearsal before production, or obtain explicit product-owner data-loss risk acceptance. See the [Supabase pausing](https://supabase.com/docs/guides/platform/free-project-pausing), [database backups](https://supabase.com/docs/guides/platform/backups), and [CLI dump](https://supabase.com/docs/reference/cli/supabase-db-dump) docs.

Track these items in [`todo.md`](todo.md) rather than treating existing source or old build output as verification evidence.
