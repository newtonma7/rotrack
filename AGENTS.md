# rotrack agent guide

## Read first

Before changing code, read:

- `arch.plan.md` — product invariants, architecture, trust boundaries, and API/data contracts.
- `todo.md` — task order, status vocabulary, acceptance criteria, and verification evidence.
- `frontend/DESIGN.md` — UI source of truth for frontend work.

Source code and recorded command output outrank stale documentation. The root `README.md` is currently create-next-app boilerplate; do not use it as the repository runbook.

## Repository map

- `frontend/` — Next.js App Router, React, TypeScript, Tailwind, Supabase Auth client, and UI.
- `backend/` — Java 21 Spring Boot API, JWT security, JPA services/repositories, and PostgreSQL access.
- `database/migrations/` — ordered Supabase PostgreSQL migrations; apply and verify migrations deliberately.
- `arch.plan.md` / `todo.md` — architecture/contracts and evidence-driven backlog.

The runtime flow is: Supabase Auth in the browser → bearer JWT → Spring API → Supabase PostgreSQL. The frontend must use the typed client in `frontend/src/lib/api.ts` for application data; it must not query application tables directly through Supabase.

## Non-negotiable domain and security rules

- Activity types are exactly `WORK` and `ROT`.
- Tracking is explicit: idle time is not inferred, and sessions are not automatically stopped on close, hide, minimize, navigation, or reload. Preserve active-session restoration and explicit stop behavior. Treat the existing unload-stop hook as legacy until removed by the relevant task.
- The server timestamps are authoritative. Clients never submit duration or `user_id`.
- A user may have at most one active session; stop is idempotent.
- Spring derives the acting user from the validated JWT `sub`. Every read and mutation is ownership-scoped; never trust a request-supplied user ID.
- Preserve `/api/v1`, the documented DTO/error contracts, and `ddl-auto: validate`.
- Do not log tokens, credentials, note content, or private reflections. Never commit real secrets; `.env.example` files document names only.
- Changes to timer lifecycle, trust boundaries, sharing/privacy, or stored rich text require an architecture decision/update before implementation.

## Development commands

Run commands from the indicated directory.

```bash
# frontend
cd frontend
npm ci
npm run dev                 # http://localhost:3000
npm run lint
npm run typecheck
npm test
npm run build

# backend
cd backend
mvn spring-boot:run         # http://localhost:8080
mvn test
mvn package
```

Frontend environment: copy `frontend/.env.example` and provide `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_KEY`, and `NEXT_PUBLIC_API_URL`. Backend environment is injected through the shell/IDE/container, not implicitly loaded from `.env`; configure the variables in `backend/.env.example`. Backend startup needs Java 21, Maven 3.9+, a configured Supabase PostgreSQL database, and applied migrations. The unauthenticated liveness endpoint is `GET /api/v1/health`.

The frontend has a minimal Vitest baseline; broader frontend coverage and backend source tests are still being established. Behavior changes must add tests as part of the change rather than treating testing as cleanup. Do not claim tests, typechecking, builds, migrations, or browser flows passed without running them.

## Change and validation workflow

1. Inspect the current diff and dirty state before editing; do not overwrite unrelated work.
2. For behavior changes, write a failing test first, implement the smallest fix, then run focused tests followed by the relevant full suite and typecheck/build.
3. User-facing flows require real-browser/Playwright validation when the harness is available; otherwise record the manual limitation plainly.
4. Add concise teaching comments only for non-obvious domain, auth, database-authorization, timer, timezone, or privacy decisions.
5. Keep generated artifacts such as `backend/target/` out of source changes. Use one writer for a file/subsystem; review the final diff and evidence before declaring work verified.

## Frontend conventions

Use App Router routes under `frontend/src/app`, feature components under `src/components`, shared primitives under `src/components/ui`, hooks under `src/hooks`, API/auth code under `src/lib`, and DTOs under `src/types`. Preserve RSC boundaries and add `"use client"` only for browser state, effects, or event handlers. Read `frontend/DESIGN.md` before UI work: use existing `--rt-*` tokens, Migha/Figtree/Digital-7 fonts, accessible controls, restrained motion, and no stock imagery or new accent colors.

## Backend conventions

Keep the layered `com.rotrack` structure: `controller`, `service`, `repository`, `model`, `dto`, `config`, and `exception`. Keep authorization at the API/service boundary and use ownership-scoped repository methods. Add backend tests under `backend/src/test/java/com/rotrack` for domain rules, security/controller boundaries, repository behavior, and migrations as appropriate.
