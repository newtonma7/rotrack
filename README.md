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
- A Supabase development project with access to its PostgreSQL database and JWT configuration
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

The backend variables are documented in [`backend/.env.example`](backend/.env.example): database connection/TLS, pool limits/timeouts, Supabase issuer/JWKS URLs and JWT audience, CORS origins, readiness caching, mutation-rate limits, structured-request-log metadata, and port. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `SUPABASE_JWKS_URI`, and `SUPABASE_ISSUER_URI` are required; startup fails rather than silently using development credentials. `DATABASE_URL` is the only TLS-mode source and managed PostgreSQL must include `sslmode=verify-full` plus an explicit `sslrootcert` path to the provider's official CA certificate. Only the explicit `local` Spring profile may use `sslmode=disable` for loopback PostgreSQL.

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

Open <http://localhost:3000>. The API listens on <http://localhost:8080> by default. Probe it with:

```bash
curl --fail-with-body http://localhost:8080/api/v1/health
curl --fail-with-body http://localhost:8080/api/v1/readiness
```

Liveness always performs no dependency calls and returns `200 {"status":"ok"}` while the process can serve HTTP. Readiness validates a database connection and returns either `200 {"status":"ready"}` or `503 {"status":"not_ready"}`. Both endpoints are unauthenticated and intentionally omit dependency details. Use liveness for ECS container restarts and readiness for traffic eligibility; see the [operations runbook](docs/operations/startup-and-health.md).

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

The repository has focused Vitest and JUnit suites for the timer, API/security boundaries, dashboard semantics, mutation rate limiting, and structured-log redaction. Opt-in PostgreSQL migration/repository checks and authenticated browser coverage require explicit isolated targets and external auth state; their latest evidence is recorded in [`todo.md`](todo.md). Do not report tests, typechecking, builds, migrations, or browser flows as passing unless they were actually run. Behavior changes must add tests as part of the same change.

## Current API surface

All paths use the `/api/v1` prefix. Authenticated endpoints require `Authorization: Bearer <supabase-jwt>`.

- `GET /health` — unauthenticated, database-independent liveness check
- `GET /readiness` — unauthenticated database readiness check (`200` ready, `503` not ready)
- `POST /time-entries/start` — start a `WORK` or `ROT` session (`201 Created`)
- `GET /time-entries/active` — get the authenticated user's active session
- `PUT /time-entries/{id}/stop` — stop an owned session by ID
- `GET /dashboard/stats?timeZone=Area%2FCity` — get timestamp-derived totals and daily buckets; optional paired `start`/`end` ISO local dates define a half-open range

The backend derives the acting user from the validated JWT subject. Clients must not send a `user_id`. Dashboard responses use seconds and UTC range instants while daily bucket labels remain local dates in the requested IANA timezone.

## Working conventions

- Read `arch.plan.md` before changing timer lifecycle, authentication, authorization, migrations, timezones, or privacy behavior.
- Tracking is explicit: sessions remain active across browser lifecycle events until explicitly stopped, and the server's timestamps are authoritative.
- Preserve the two activity buckets: `WORK` and `ROT`.
- Keep frontend App Router code under `frontend/src/app`, feature components under `frontend/src/components`, hooks under `frontend/src/hooks`, API/auth code under `frontend/src/lib`, and types under `frontend/src/types`.
- Read `frontend/DESIGN.md` before UI work. Preserve the Tangerine Studio tokens, typography, accessibility, restrained motion, and no-stock-imagery rule.
- Keep backend layers under `com.rotrack`: controllers, services, repositories, models, DTOs, configuration, and exceptions.
- Scope every backend read and mutation to the authenticated user. Never log tokens, credentials, private notes, or reflections.

## Known limitations

The repository is still working toward the production-ready MVP. In particular:

- Empty-database migration application, migrated-database repository checks, the dedicated runtime-role audit, and direct Data API RLS evidence pass against isolated/development targets. A fresh disposable signup reached confirmation, but opening that confirmation email and completing the same user's first sign-in remain externally blocked.
- The authenticated two-user Spring ownership matrix and required-authenticated Playwright 4/4 flow are recorded as passing. Those local/development results are not deployed-staging evidence.
- Managed-CA startup, independent liveness/readiness, degraded readiness `503`, and exact CORS behavior are locally verified. Hosted CI/protection, an authorized isolated staging deployment, fleet-wide/authentication-adjacent edge limiting, observed telemetry/alerts, staging smoke, and rollback rehearsal remain open.

Track these items in [`todo.md`](todo.md) rather than treating existing source or old build output as verification evidence.
