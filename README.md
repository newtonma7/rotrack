# rotrack

rotrack is a personal study and productivity tracker. The MVP records explicit `WORK` and `ROT` sessions, authenticates with Supabase, and displays a private seven-day dashboard.

## Source of truth

Read these before changing the repository:

- [`arch.plan.md`](arch.plan.md) — product invariants, architecture, security boundaries, and API/data contracts.
- [`todo.md`](todo.md) — dependency order, acceptance criteria, status, and verification evidence.
- [`frontend/DESIGN.md`](frontend/DESIGN.md) — frontend visual and interaction design.
- [`AGENTS.md`](AGENTS.md) — repository-specific development instructions.

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

The backend variables are documented in [`backend/.env.example`](backend/.env.example): database connection, Supabase issuer/JWKS URLs, JWT audience, CORS origins, and port.

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

Open <http://localhost:3000>. The API listens on <http://localhost:8080> by default. Check API liveness with:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{"status":"ok"}
```

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

The repository has focused Vitest and JUnit suites for the timer, API/security boundaries, and dashboard semantics. Database-backed migration/repository checks and authenticated browser coverage require explicit local development setup and remain open. Do not report tests, typechecking, builds, migrations, or browser flows as passing unless they were actually run. Behavior changes must add tests as part of the same change.

## Current API surface

All paths use the `/api/v1` prefix. Authenticated endpoints require `Authorization: Bearer <supabase-jwt>`.

- `GET /health` — unauthenticated liveness check
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

- Migration hardening exists, but repeatable PostgreSQL-backed migration/constraint tests and current remote catalog evidence are still missing.
- Real Supabase-signed JWT, two-user ownership/RLS, and application-role grant proof remain incomplete.
- Test coverage is focused rather than complete; PostgreSQL integration, Playwright, CI, staging, readiness, and deployment work remain open.

Track these items in [`todo.md`](todo.md) rather than treating existing source or old build output as verification evidence.
