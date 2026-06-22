# Role

You are an expert pair-programming AI and senior full-stack architect. Your goal is to help me build **rotrack**, a web application, by executing the phased plan below. You will write clean, modern, and highly maintainable code following the exact constraints provided.

# Project Context & Core Domain

**rotrack** is a low-friction time-tracking app designed for honest productivity logging.

* **The Two-Bucket Rule:** There are ONLY two activities: **Rot** (passive consumption, doomscrolling) and **Work** (intentional focus).
* **No Stagnant:** The legacy third "stagnant" bucket is deprecated everywhere. If it is not active "Work", it defaults to "Rot". Proactively remove any references to "stagnant" or three-bucket arrays/enums.
* **Architecture reference:** See [`arch.plan.md`](arch.plan.md) for full system design, API spec, and future features.

# Tech Stack & Architecture

* **Frontend:** Next.js 16 (App Router), React 19, TypeScript 5 — lives in `frontend/`
* **Styling:** Tailwind CSS v4 (`@tailwindcss/postcss`) + custom CSS variables (`--rt-*`). Reference [`frontend/DESIGN.md`](frontend/DESIGN.md).
* **UI Components:** shadcn/ui (new-york style, lucide-react icons, `cn()` in `src/lib/utils.ts`)
* **Auth:** Supabase Auth (`@supabase/supabase-js`). Sign-in returns to `/`. `/tracker` and `/dashboard` require auth.
* **Backend:** Spring Boot 3.x REST API — lives in `backend/`
* **API Security:** Stateless JWT validation via OAuth2 Resource Server (Supabase JWKS)
* **Database:** Supabase PostgreSQL with Row Level Security (RLS). Migrations in `database/migrations/`

# UI & Component Constraints

1. **Tailwind + shadcn ONLY:** No raw CSS or inline styles. Use shadcn for all interactive elements.
2. **Theming:** Map shadcn CSS variables to `--rt-*` brand tokens in `globals.css`. No legacy `landing-gradient-*` classes.
3. **Tracker UI:** Use `ActiveTracker` on `/tracker` — not the deleted `Clock3DLED` component. Landing hero clock stays inline in `page.tsx`.

# Auto-Stop Edge Case Logic

* If the user leaves the app (closes tab, minimizes, navigates away), the active timer MUST stop.
* Implemented via `usePageVisibilityStop` — `visibilitychange` + `fetch(..., { keepalive: true })` to the stop endpoint.

# Development Standards

*Persistent process rules also live in [`.cursor/rules/rotrack-dev-standards.mdc`](.cursor/rules/rotrack-dev-standards.mdc).*

## Learning-mode comments

The primary developer is learning the stack. When writing or changing code, add **teachable inline comments** so each file explains itself:

1. **File header** (2–5 lines): purpose, layer (frontend / API / DB), and main dependencies.
2. **Block comments** before non-obvious logic: auth, JWT → API, hooks, RLS, timer/auto-stop, enums, state/effects.
3. Explain **why** and **what breaks if wrong** — not restatements of obvious syntax.
4. Trace **data flow** across boundaries: `Browser → Next.js → Spring Boot → Supabase Postgres`.
5. Skip comments on trivial layout or boilerplate shadcn usage.

## Phase gates (verify before advancing)

- **No phase is complete** until that phase's **Exit criteria** below are checked and proof is recorded.
- **Proof format** (append under the phase): commands run, HTTP status/body snippets, routes clicked, or known blockers.
- Before starting phase *N+1*, confirm phase *N* exit criteria are all `[x]` or explicitly deferred with reason.
- Every finished task should include: learning comments in touched files + verification from the current phase exit list.

---

# Progress Summary

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Foundation & cleanup | **Done** |
| 1 | Backend & database genesis | **Scaffolded** — code exists; migration not yet applied to Supabase |
| 2 | MVP frontend integration | **Done** — needs live API + DB to verify end-to-end |
| 3 | Ops, env & integration hardening | **Next** |
| 4 | Full API & data model (arch.plan MVP gap) | Not started |
| 5 | Frontend polish & component architecture | Not started |
| 6 | Future product features | Not started |
| 7 | Testing | Not started |
| 8 | Deployment & CI/CD | Not started |
| 9 | Documentation & observability | Not started |

---

# Execution Plan (The Backlog)

Ask which Phase or Task to start with. When executing a task, analyze relevant files first, confirm approach, then code.

Reference: [`arch.plan.md`](arch.plan.md) — sections *Implementation Approach*, *API Design*, *Future Extension Tables*, *Testing Strategy*, *Deployment Strategy*.

---

## Phase 0: Foundation & Cleanup — DONE

- [x] **Task 0.1: Two-Bucket Purge**
  - Removed stagnant from `page.tsx`, `dashboard/page.tsx`, `DESIGN.md`, `arch.plan.md`
  - Enum is `ROT | WORK` only
- [x] **Task 0.2: Consolidate Styling**
  - Dashboard, auth, tracker migrated to light Tangerine Studio (`--rt-*`)
  - Removed `landing-gradient-*` from `globals.css`
- [x] **Task 0.3: Auth UI Refactor**
  - shadcn `Input`, `Label`, `Button`, `Card` on sign-in/sign-up
  - Sign-in redirects to `/` (landing stays public)
  - Route guards on `/dashboard` and `/tracker` via layout files
- [x] **Task 0.4: Legacy Removal**
  - Deleted: `Clock3DLED.tsx`, `/timing`, `/home`, `/aboutus`, empty `architecture.md`
  - Removed unused Three.js dependencies
  - Removed About links from landing nav/footer

**Phase 0 exit criteria** *(retroactive — confirm if revisiting)*

- [x] No `stagnant` / three-bucket references in app code or design docs
- [x] `npm run build` passes in `frontend/`
- [x] Routes: `/`, `/signin`, `/signup`, `/dashboard`, `/tracker` (no `/home`, `/timing`, `/aboutus`)
- [ ] Learning comments added to key files touched in Phase 0 (ongoing as we revisit code)

**Proof log:**

- _(add notes when re-verifying)_

---

## Phase 1: Backend & Database Genesis — SCAFFOLDED

- [x] **Task 1.1: Database Schema & RLS (migration file)**
  - `database/migrations/001_initial_schema.sql` — `users`, `time_entries`, `ROT|WORK` enum, RLS, auth signup trigger
  - [ ] **1.1b: Apply migration to Supabase project** (run SQL in Supabase dashboard or CLI)
- [x] **Task 1.2: Spring Boot Setup**
  - `backend/` initialized with JWT validation via Supabase JWKS, CORS, `application.yml`
- [x] **Task 1.3: Core API Endpoints (MVP subset)**
  - `POST /time-entries/start`, `PUT /time-entries/{id}/stop`, `PUT /time-entries/active/stop`
  - `GET /time-entries/active`, `GET /dashboard/stats`, `GET /health`

**Phase 1 exit criteria**

- [x] Migration SQL exists at `database/migrations/001_initial_schema.sql`
- [x] Spring Boot project compiles (when Java 21 + Maven available)
- [ ] Migration **applied** to Supabase dev project
- [ ] `GET http://localhost:8080/api/v1/health` returns `{"status":"ok"}` with backend running
- [ ] Learning comments on `SecurityConfig`, `TimeEntryService`, and controllers explain JWT + session flow

**Proof log:**

- _(fill after Phase 3 backend wiring)_

---

## Phase 2: Core MVP Frontend Integration — DONE

- [x] **Task 2.1: API Client**
  - `frontend/src/lib/api.ts` — dynamic Supabase JWT, typed responses
  - `frontend/src/types/time-entry.ts`
  - `frontend/.env.example` with `NEXT_PUBLIC_API_URL`
- [x] **Task 2.2: Tracker Route**
  - `/tracker` with `ActiveTracker` component + `useTimeTracking` hook
  - Legacy `/timing` and `/home` removed
- [x] **Task 2.3: Auto-Stop Hook**
  - `frontend/src/hooks/usePageVisibilityStop.ts`
- [x] **Task 2.4: Dashboard Wiring**
  - `/dashboard` fetches from `GET /dashboard/stats` with graceful empty-state fallback

**Phase 2 exit criteria**

- [x] `frontend/src/lib/api.ts`, hooks, `/tracker`, `/dashboard` exist
- [x] `npm run build` passes
- [ ] Live API connected: start/stop session updates dashboard stats (depends on Phase 3)
- [ ] Auto-stop observed in Network tab when tab hidden
- [x] Learning comments on `api.ts`, `useTimeTracking`, `usePageVisibilityStop`, `ActiveTracker`, `types/time-entry.ts`, dashboard fetch, route layouts

**Proof log:**

- _(fill after Phase 3 E2E smoke test)_

---

## Phase 3: Ops, Environment & Integration Hardening — NEXT

*Aligns with arch.plan § Implementation Approach → Integration + Development environment*

- [ ] **Task 3.1: Supabase Project Wiring**
  - Apply `001_initial_schema.sql` to dev Supabase project
  - Verify RLS: users can only read/write own rows
  - Confirm auth signup trigger creates `public.users` row
- [ ] **Task 3.2: Backend Environment**
  - Configure `DATABASE_URL`, `SUPABASE_JWKS_URI`, `SUPABASE_ISSUER_URI` per `backend/.env.example`
  - Install Java 21 + Maven; verify `mvn spring-boot:run` starts on `:8080`
- [ ] **Task 3.3: Frontend Environment**
  - Set `NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1` in `frontend/.env.local`
  - Verify fonts in `public/fonts/` and hero webp in `public/`
- [ ] **Task 3.4: End-to-End Smoke Test**
  - Sign up → sign in → start Work session on `/tracker` → stop → see stats on `/dashboard`
  - Verify auto-stop fires when tab is hidden
  - Fix any CORS, JWT issuer, or enum mapping issues discovered

**Phase 3 exit criteria** *(required before Phase 4)*

- [ ] `001_initial_schema.sql` applied; new signup creates `public.users` row
- [ ] RLS verified: user A cannot read user B's `time_entries`
- [ ] Backend runs locally with valid env; health check passes
- [ ] Frontend `.env.local` has Supabase + `NEXT_PUBLIC_API_URL`
- [ ] E2E: sign up → sign in → `/tracker` start WORK → stop → `/dashboard` shows non-empty stats
- [ ] Tab hide triggers stop request (`keepalive` PUT) with 2xx response
- [ ] Phase 1 & 2 exit criteria marked complete with proof logs below
- [ ] Learning comments added/updated on integration touchpoints (`api.ts`, auth layouts, backend service layer)

**Proof log:**

```
# Example — replace with real output
# curl http://localhost:8080/api/v1/health
# npm run build (frontend)
# mvn spring-boot:run (backend)
# Steps: ...
```

---

## Phase 4: Full API & Data Model — arch.plan MVP gaps

*Aligns with arch.plan § API Design + Database Schema (user_preferences)*

- [ ] **Task 4.1: user_preferences Table**
  - Migration: `timezone`, `daily_goal_hours`, RLS policies
  - Spring entity + repository
- [ ] **Task 4.2: Time Entry CRUD (full)**
  - `GET /time-entries` — paginated, filtered by date range and activity type
  - `POST /time-entries` — manual entry with start/end
  - `GET /time-entries/{id}`, `PUT /time-entries/{id}`, `DELETE /time-entries/{id}`
- [ ] **Task 4.3: User Profile & Preferences API**
  - `GET/PUT /user/profile`, `GET/PUT /user/preferences`
  - `UserController`, `UserService`
- [ ] **Task 4.4: Extended Dashboard Endpoints**
  - `GET /dashboard/summary` — current day/week snapshot
  - `GET /dashboard/trends` — time series with `granularity` param (day/week/month)
  - Query param support on `/dashboard/stats` (`startDate`, `endDate`, `granularity`)
- [ ] **Task 4.5: API Documentation**
  - SpringDoc OpenAPI (Swagger UI) per arch.plan § Technology Stack

**Phase 4 exit criteria** *(required before Phase 5)*

- [ ] `user_preferences` migration applied; CRUD works via API
- [ ] Full time-entry CRUD tested with Postman/curl (list, create, update, delete)
- [ ] Profile + preferences endpoints return/update correct user data
- [ ] `/dashboard/summary` and `/dashboard/trends` return expected shapes for frontend
- [ ] Swagger UI loads and documents all public endpoints
- [ ] Learning comments on new entities, controllers, and DTOs

**Proof log:**

- _(curl examples, Swagger URL, migration version)_

---

## Phase 5: Frontend Polish & Component Architecture

*Aligns with arch.plan § Frontend Component Architecture + Component Hierarchy*

- [ ] **Task 5.1: Extract Dashboard Components**
  - `components/dashboard/TimeChart.tsx`, `ActivitySummary.tsx`, `WeeklyStats.tsx`
  - Refactor `dashboard/page.tsx` into composed components
- [ ] **Task 5.2: Extract Tracker Components**
  - `ActivityTimer.tsx`, `ActivityButtons.tsx`, `RecentEntriesList.tsx`
  - `TimeLogEditor.tsx` — modal/form for editing past entries
- [ ] **Task 5.3: Server State Caching**
  - Add React Query (or SWR) for dashboard stats and active session
  - Loading skeletons and optimistic updates on start/stop
- [ ] **Task 5.4: Settings Page**
  - Wire sidebar Settings nav to `/settings`
  - Profile edit + preferences (timezone, daily goal) via user API
- [ ] **Task 5.5: Form Validation**
  - React Hook Form + Zod on auth, time log editor, settings forms
- [ ] **Task 5.6: Auth Hardening (optional)**
  - Evaluate `@supabase/ssr` + middleware for cookie-based session
  - Email confirmation UX polish

**Phase 5 exit criteria** *(required before Phase 6)*

- [ ] Dashboard and tracker decomposed into named components per arch.plan hierarchy
- [ ] React Query (or SWR) caches dashboard + active session; no duplicate fetches on navigation
- [ ] `/settings` edits profile/preferences end-to-end
- [ ] Forms use Zod validation with clear error messages
- [ ] `npm run build` passes; manual UX pass on tracker, dashboard, settings, auth
- [ ] Learning comments on extracted components and query hooks

**Proof log:**

- _(component list, settings flow steps)_

---

## Phase 6: Future Product Features

*Aligns with arch.plan § Future Extension Tables + Phase 2: Future Features*

- [ ] **Task 6.1: Goal Tracking**
  - `goals` table migration (title, target_hours, target_date, activity_type, status)
  - Goal CRUD API + UI; progress toward daily/weekly targets
  - Optional milestone notifications
- [ ] **Task 6.2: Study Groups**
  - `study_groups`, `study_group_members` tables
  - Group management UI; aggregated stats (privacy-preserving, no individual entry access)
  - Group challenges / competitions
- [ ] **Task 6.3: Advanced Analytics**
  - Weekly/monthly reports; productivity insights; pattern analysis
  - Export (CSV, PDF)
  - Dashboard date-range picker wired to API query params

**Phase 6 exit criteria** *(required before Phase 7)*

- [ ] Each shipped feature (goals / groups / analytics) has migration + API + UI + manual test script
- [ ] Study group stats do not leak individual entries (privacy check documented)
- [ ] Export produces valid CSV/PDF for sample account
- [ ] Learning comments on new domain logic (goals, aggregation, export)

**Proof log:**

- _(feature checklist per 6.1–6.3)_

---

## Phase 7: Testing

*Aligns with arch.plan § Testing Strategy*

- [ ] **Task 7.1: Backend Tests**
  - JUnit 5 unit tests for `TimeEntryService`
  - MockMvc controller tests; `@DataJpaTest` for repositories
  - TestContainers for integration tests (optional)
- [ ] **Task 7.2: Frontend Tests**
  - Jest + React Testing Library for `ActiveTracker`, auth forms
  - Integration tests for API client
- [ ] **Task 7.3: E2E Tests (optional)**
  - Playwright or Cypress: sign-in → track → dashboard flow
- [ ] **Task 7.4: Migration Tests**
  - Verify schema constraints, foreign keys, RLS policy behavior

**Phase 7 exit criteria** *(required before Phase 8)*

- [ ] Backend test suite passes (`mvn test`)
- [ ] Frontend unit tests pass (`npm test` or documented test command)
- [ ] CI runs lint + tests on PR (local dry-run acceptable before pipeline exists)
- [ ] RLS / constraint tests documented or automated
- [ ] Optional E2E: sign-in → track → dashboard green in Playwright/Cypress

**Proof log:**

- _(test commands + pass counts)_

---

## Phase 8: Deployment & CI/CD

*Aligns with arch.plan § Deployment Strategy + Infrastructure*

- [ ] **Task 8.1: Containerize Backend**
  - Dockerfile for Spring Boot; health check on `/api/v1/health`
- [ ] **Task 8.2: Frontend Deployment**
  - Deploy Next.js to Vercel (or AWS Amplify per arch.plan)
  - Configure production env vars (Supabase, API URL)
- [ ] **Task 8.3: Backend Deployment**
  - AWS ECS Fargate or EC2 (per arch.plan); staging + production Supabase projects
- [ ] **Task 8.4: CI/CD Pipeline**
  - GitHub Actions: lint, test, build frontend + backend on PR
  - Automated deploy to staging on merge
- [ ] **Task 8.5: Production Hardening**
  - HTTPS enforcement, rate limiting, secrets management
  - CORS locked to production frontend domain

**Phase 8 exit criteria** *(required before Phase 9)*

- [ ] Staging URLs live for frontend + backend; health check public
- [ ] Production env vars set (no secrets in repo)
- [ ] CI/CD deploys staging on merge; rollback path documented
- [ ] Smoke test passes against staging (same script as Phase 3 E2E)
- [ ] HTTPS + CORS verified from production frontend origin

**Proof log:**

- _(staging URLs, deploy command, smoke result)_

---

## Phase 9: Documentation & Observability

*Aligns with arch.plan § Documentation + Monitoring and Observability*

- [ ] **Task 9.1: Project README**
  - Replace default create-next-app README with monorepo setup guide
  - Root README linking `frontend/`, `backend/`, `database/`
- [ ] **Task 9.2: API & Code Docs**
  - Swagger UI live; Javadoc on services; JSDoc on shared frontend utilities
- [ ] **Task 9.3: Observability**
  - Structured logging (Logback backend); error tracking (Sentry or similar)
  - Optional APM; Supabase dashboard monitoring for DB
- [ ] **Task 9.4: User Guide**
  - Short end-user doc: two buckets, tracker, dashboard, auto-stop behavior

**Phase 9 exit criteria** *(project MVP documentation complete)*

- [ ] Root `README.md` documents full local setup (frontend + backend + Supabase)
- [ ] Swagger/OpenAPI published; new dev can call APIs without reading source
- [ ] Error tracking or structured logs confirmed in staging/prod
- [ ] User guide covers two-bucket model, tracker, dashboard, auto-stop
- [ ] `todo.md` progress table reflects final phase status with proof links

**Proof log:**

- _(README sections, doc URLs)_

---

**Cursor:** Phases 0–2 are implemented in code. Follow **Development Standards** (learning comments + phase gates). Start with **Phase 3** unless directed otherwise. Do not advance past a phase until its exit criteria are checked and proof is logged.
