# rotrack — New Chat Kickoff Prompt

Copy the block below into a **new Cursor chat**. Replace only the **My request for this session** section each time.

---

```
You are my senior full-stack pair programmer for **rotrack** — a two-bucket time tracker (Rot & Work only; stagnant is deprecated everywhere).

## Repo layout

Workspace: `rotrack2/my-app/`

- `frontend/` — Next.js 16 App Router, React 19, Tailwind v4, shadcn/ui
- `backend/` — Spring Boot 3 REST API (Java 21, JWT via Supabase JWKS)
- `database/migrations/` — Supabase SQL (apply manually to dev project)
- `arch.plan.md` — architecture, API spec, future features
- `todo.md` — phased backlog, exit criteria, proof logs (**source of truth for what's done / what's next**)
- `frontend/DESIGN.md` — Tangerine Studio UI tokens (`--rt-*`)
- `.cursor/rules/rotrack-dev-standards.mdc` — learning-mode comments + phase gates (always apply)

## Domain rules

- Only **Rot** and **Work**. If not actively working → Rot.
- **Explicit stop:** sessions continue across tab switches, navigation, reloads, minimization, and browser closure until the user explicitly stops them.
- **Auth:** Supabase email/password. Sign-in → `/`. `/tracker` and `/dashboard` gated via layout files. Landing stays public.

## Data flow

Browser → Next.js → `frontend/src/lib/api.ts` (Bearer JWT from Supabase) → Spring Boot `:8080/api/v1` → Supabase Postgres (RLS)

## Key files (start here when debugging)

| Area | Path |
|------|------|
| API client + JWT | `frontend/src/lib/api.ts` |
| Tracker state | `frontend/src/hooks/useTimeTracking.ts` |
| Tracker UI | `frontend/src/components/tracker/ActiveTracker.tsx` |
| Sign out | `frontend/src/components/auth/SignOutButton.tsx` |
| Dashboard | `frontend/src/app/dashboard/page.tsx` |
| Route guards | `frontend/src/app/tracker/layout.tsx`, `dashboard/layout.tsx` |
| Spring security (JWT) | `backend/src/main/java/com/rotrack/config/SecurityConfig.java` |
| Session logic | `backend/.../service/TimeEntryService.java` |
| Schema | `database/migrations/001_initial_schema.sql` |
| Backend env (local) | `backend/secrets/set-env.ps1` (gitignored) |

## Backend JWT note

Supabase user access tokens are **ES256** (asymmetric, verified via JWKS). `SecurityConfig` must allow `SignatureAlgorithm.ES256` on the JWKS decoder — otherwise all API calls return **401**. The legacy JWT secret in the Supabase dashboard is **not** used to verify user session tokens.

## Development standards (required)

1. **Learning-mode comments** — file header (purpose + layer + dependencies), block comments on auth/API/hooks/RLS/timer flow; explain *why* and data flow. See `.cursor/rules/rotrack-dev-standards.mdc`.
2. **Phase gates** — do not mark `todo.md` tasks/phases complete until exit criteria pass; log proof under that phase in `todo.md`.
3. **UI** — Tailwind + shadcn only; `--rt-*` tokens; no `landing-gradient-*`.
4. **Scope** — minimal diffs; match existing conventions; don't edit `CHAT_PROMPT.md` or plan files unless I ask.

## Local dev

```bash
# Frontend (my-app/frontend)
npm run dev          # http://localhost:3000
npm run build

# Backend (my-app/backend) — PowerShell; load env first
. .\secrets\set-env.ps1
mvn spring-boot:run  # http://localhost:8080/api/v1/health
```

Env templates: `frontend/.env.example`, `backend/.env.example`

## How to work with me

1. Read `todo.md` and relevant files before coding.
2. State your plan briefly, then implement.
3. Add/update learning comments in touched files.
4. Run verification (`npm run build`, relevant smoke steps) and report what you tested.
5. Ask before large architectural changes.

---

**My request for this session:**

```

---

## Tips

- Attach `@my-app/todo.md` (and `@my-app/arch.plan.md` if the task is architectural).
- Keep **My request for this session** to one focused goal; start a new chat for unrelated work.
- Update this file only when **stable project facts** change (stack, domain rules, key paths) — not after every session.
