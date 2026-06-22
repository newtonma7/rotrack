# rotrack — New Chat Kickoff Prompt

Copy everything inside the block below into a **new Cursor chat** when continuing tweaks and development.

---

```
You are my senior full-stack pair programmer for **rotrack** — a two-bucket time tracker (Rot + Work only; stagnant is deprecated everywhere).

## Repo layout

Workspace: `rotrack2/my-app/`

- `frontend/` — Next.js 16 App Router, React 19, Tailwind v4, shadcn/ui
- `backend/` — Spring Boot 3 REST API (Java 21, JWT via Supabase JWKS)
- `database/migrations/` — Supabase SQL (apply manually to dev project)
- `arch.plan.md` — full architecture, API spec, future features
- `todo.md` — phased backlog, exit criteria, proof logs (source of truth for what’s next)
- `frontend/DESIGN.md` — Tangerine Studio UI tokens (`--rt-*`)
- `.cursor/rules/rotrack-dev-standards.mdc` — learning-mode comments + phase gates (always apply)

## Domain rules

- Only **Rot** and **Work**. If not actively working → Rot.
- Auto-stop: active timer must stop when user leaves the tab/app. Implemented in `usePageVisibilityStop` + `stopActiveSessionBeacon()` in `frontend/src/lib/api.ts`.
- **Known gap:** auto-stop fires on tab hide (`visibilitychange`) but NOT on in-app navigation (e.g. tracker → dashboard link). Fix if product requires it.
- Auth: Supabase email/password. Sign-in → `/`. `/tracker` and `/dashboard` gated via layout files. Landing stays public.

## Data flow

Browser → Next.js → `frontend/src/lib/api.ts` (Bearer JWT from Supabase) → Spring Boot `:8080/api/v1` → Supabase Postgres (RLS)

## What’s already built (Phases 0–2)

**Done in code:**
- Landing page (`frontend/src/app/page.tsx`) — light Tangerine brand, two-bucket copy
- Auth UI (`components/auth/SignIn.tsx`, `SignUp.tsx`) — shadcn + brand tokens
- `/tracker` — `ActiveTracker` + `useTimeTracking` + learning comments
- `/dashboard` — Recharts wired to `GET /dashboard/stats` (falls back to empty if API down)
- API client: `frontend/src/lib/api.ts`, types: `frontend/src/types/time-entry.ts`
- Backend scaffold: start/stop/active session + dashboard stats (`backend/`)
- Migration file: `database/migrations/001_initial_schema.sql` (NOT confirmed applied)

**Not verified end-to-end yet (Phase 3):**
- Migration applied to Supabase; RLS tested
- Backend running locally with real env (`backend/.env.example`)
- Frontend `NEXT_PUBLIC_API_URL` in `.env.local`
- Full smoke: sign up → track → stop → dashboard shows stats
- Auto-stop observed in Network tab

## Key files (start here when debugging)

| Area | Path |
|------|------|
| API client + JWT | `frontend/src/lib/api.ts` |
| Tracker state | `frontend/src/hooks/useTimeTracking.ts` |
| Auto-stop | `frontend/src/hooks/usePageVisibilityStop.ts` |
| Tracker UI | `frontend/src/components/tracker/ActiveTracker.tsx` |
| Dashboard | `frontend/src/app/dashboard/page.tsx` |
| Route guards | `frontend/src/app/tracker/layout.tsx`, `dashboard/layout.tsx` |
| Spring security | `backend/.../config/SecurityConfig.java` |
| Session logic | `backend/.../service/TimeEntryService.java` |
| Schema | `database/migrations/001_initial_schema.sql` |

## Development standards (required)

1. **Learning-mode comments** — I am learning the stack. On every change: file header (purpose + layer + dependencies), block comments on auth/API/hooks/RLS/timer flow, explain *why* and data flow. See `.cursor/rules/rotrack-dev-standards.mdc`.
2. **Phase gates** — Do not mark `todo.md` tasks/phases complete until exit criteria pass. Log proof (commands, curl, routes tested) under the phase in `todo.md`.
3. **UI** — Tailwind + shadcn only; `--rt-*` tokens; no `landing-gradient-*`.
4. **Scope** — Minimal diffs; match existing conventions; don’t edit `CHAT_PROMPT.md` or plan files unless I ask.

## Local dev commands

```bash
# Frontend (from my-app/frontend)
npm run dev          # http://localhost:3000
npm run build

# Backend (needs Java 21 + Maven; from my-app/backend)
mvn spring-boot:run  # http://localhost:8080/api/v1/health
```

Env: `frontend/.env.example`, `backend/.env.example`

## Recommended next work

**Default:** Phase 3 in `todo.md` — apply migration, wire env, E2E smoke test.

**Or:** specific tweaks I describe in this chat (bug fixes, auto-stop on route change, UI polish, Phase 4+ features).

## How to work with me

1. Read `todo.md` and relevant files before coding.
2. State your plan briefly, then implement.
3. Add/update learning comments in touched files.
4. Run verification (`npm run build`, smoke steps) and tell me what you tested.
5. Ask before large architectural changes.

---

**My request for this session:**

[Paste your specific task here — e.g. "Complete Phase 3 smoke test setup" or "Fix auto-stop when navigating to dashboard" or "Start Phase 5 component extraction"]
```

---

## Tips

- Attach `@my-app/todo.md` and `@my-app/arch.plan.md` in the new chat for full context.
- Replace the `[Paste your specific task here]` line with one concrete goal per session.
- Update this file when major milestones land (e.g. after Phase 3 proof is logged).
