# rotrack Architecture

**Status:** Living architecture and contract document
**Last reviewed:** 2026-08-07
**Delivery backlog:** [`todo.md`](todo.md)

## 1. Purpose and Product Boundaries

rotrack is a low-friction study and productivity tracker. Its first production release focuses on reliable authentication, explicit time tracking, and a useful personal dashboard. Notes, daily logs, friends, presence, and study groups are planned product slices built after the core tracker is production-ready.

### Core domain rules

1. There are exactly two activity types: `WORK` and `ROT`. `STAGNANT` and other buckets are invalid everywhere.
2. Tracking is explicit. Time is recorded only after the user starts a Work or Rot session; idle time is untracked and is not inferred as Rot.
3. A user may have at most one active session.
4. A session remains active until the user explicitly stops it. Closing, hiding, minimizing, or navigating away from the app does not stop it.
5. Returning to the app restores the original active session, regardless of its age, until the user stops it.
6. Timestamps are authoritative. Clients never submit calculated duration or a `user_id`.
7. Completed entries must not overlap for the same user. This rule becomes user-facing when manual entry editing is added.
8. Notes, daily reflections, Rot activity, and raw session history are private unless a later architecture decision explicitly introduces sharing.

### MVP and non-goals

The production MVP includes:

- Supabase email/password authentication
- Protected tracker and dashboard routes
- Start, restore, and explicitly stop Work or Rot sessions
- A personal seven-day dashboard
- Ownership isolation, automated tests, CI, and a staging deployment

The MVP does not include manual-entry CRUD, settings, notes, daily logs, friends, groups, notifications, exports, or automatic timer stopping.

## 2. Current Repository State

This section describes what exists in source control today. It is not a completion claim; verification status and evidence live in [`todo.md`](todo.md).

### Frontend

- Next.js 16 App Router, React 19, TypeScript, Tailwind CSS 4, shadcn/ui primitives, Recharts, and `@supabase/supabase-js`
- Routes: `/`, `/signin`, `/signup`, `/signup/confirmation`, `/tracker`, `/dashboard`
- Client-side Supabase session context and protected-route layouts
- API client using native `fetch` and Supabase bearer tokens
- Tracker UI with start, active-session restore, elapsed display, and explicit stop; sessions remain active across reload, navigation, tab changes, minimization, and browser closure until explicitly stopped
- Dashboard using the timestamp-derived daily contract from `GET /api/v1/dashboard/stats`, with the browser IANA timezone

### Backend and database

- Spring Boot 3.4, Java 21 target, Maven, Spring Web, JPA, OAuth2 Resource Server, validation, and PostgreSQL
- Implemented endpoints: independent liveness/readiness, start session, get active session, ID-based stop, and dashboard stats
- Supabase migrations defining `users`, `time_entries`, the `ROT|WORK` enum, ownership RLS policies, signup profile creation, timestamp constraints, one-active-session uniqueness, and reporting indexes
- Source-controlled suites include executable PostgreSQL migration/repository tests, generated signed-JWT filter tests, service/controller ownership tests, Vitest/RTL coverage, and a quarantined external-auth Playwright critical path
- Backend startup validates managed JDBC hostname verification with an explicit CA path, exact CORS origins, bounded Hikari settings, and cached/single-flight database readiness

### Known baseline problems

- The PostgreSQL verification suite has current rollback-only evidence against the configured development schema, but empty-database application evidence and the HTTP Data API two-user RLS matrix remain open.
- Spring JDBC does not propagate the user JWT into PostgreSQL. The dedicated `rotrack_runtime` role bypasses RLS with only the required application DML, and the authenticated two-user browser flow now proves Spring ownership isolation; direct Data API RLS remains open.
- Generated ES256/RS256 failure tests cover the production decoder/filter boundary. The authenticated browser flow uses real Supabase sign-in, while a redacted direct token/API evidence record remains open.
- The Playwright harness has external two-user auth states and a passing authenticated run. Managed-CA startup is locally verified; empty-database apply, direct Data API RLS, fresh signup, dependency-failure readiness, CI, staging, rate limiting, and production observability remain open.

## 3. Target System Architecture

```text
Browser / Next.js
  |-- Supabase Auth SDK ----------> Supabase Auth
  |       receives user JWT
  |
  `-- HTTPS + Bearer JWT ---------> Spring Boot API
                                     |-- validates JWT and ownership
                                     |-- applies domain rules
                                     `-- TLS JDBC ------------------> Supabase PostgreSQL
```

### Trust boundaries

- The browser uses Supabase directly only for authentication.
- Spring Boot is the sole application-data API. The frontend does not query application tables through the Supabase Data API.
- Spring derives the user UUID from the validated JWT `sub` claim. Request bodies and query parameters never select the acting user.
- Every repository read and mutation is scoped by that UUID, including lookup by resource ID.
- PostgreSQL uses a dedicated, TLS-enabled Spring application role with only required DML grants and no schema-management privileges. That role is explicitly allowed to bypass RLS for the granted application tables because Spring cannot supply `auth.uid()` through pooled JDBC requests. Supabase RLS remains enabled for browser/Data API access; Spring's mandatory ownership-scoped queries are its authorization boundary.
- Any future direct database access requires a new architecture decision and two-user RLS tests before release.

### Fixed technology choices

| Concern | Decision |
|---|---|
| Frontend | Next.js App Router, React, TypeScript |
| UI | Tailwind CSS 4 and shadcn/ui; Recharts for charts |
| HTTP | Native `fetch` through one typed API client |
| Client server-state | TanStack Query when shared caching is introduced |
| Authentication | Supabase Auth browser client |
| Backend | Java 21, Spring Boot, Spring MVC, Spring Data JPA |
| JWT validation | Spring Security OAuth2 Resource Server |
| Database | Supabase PostgreSQL |
| Migrations | Ordered SQL migrations applied with the Supabase CLI |
| Backend tests | JUnit 5, MockMvc, repository integration tests |
| Frontend tests | Vitest and React Testing Library |
| Browser tests | Playwright |
| Hosting | Vercel frontend, AWS ECS Fargate backend, Supabase Auth/PostgreSQL |

## 4. Core Data and Time Model

### MVP tables

#### `users`

- `id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE`
- `email TEXT UNIQUE NOT NULL`
- `username TEXT UNIQUE NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

#### `time_entries`

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `activity_type activity_type NOT NULL` where the enum is only `ROT | WORK`
- `start_time TIMESTAMPTZ NOT NULL`
- `end_time TIMESTAMPTZ NULL`; `NULL` means active
- `notes TEXT NULL` as a short session label until rich-text notes ship
- audit timestamps

Required invariants and indexes:

- `end_time IS NULL OR end_time > start_time`
- Partial unique index on `user_id WHERE end_time IS NULL`
- Composite reporting index on `(user_id, start_time)`
- Timestamps, not a client-writable duration field, are the source of truth. Duration is derived from `end_time - start_time` for reads and aggregation.

If the existing `duration_minutes` column is retained temporarily, the API must ignore client values and treat it as transitional derived data. A migration must remove it after all queries derive duration from timestamps.

### Time semantics

- Persist and exchange ISO-8601 UTC instants.
- Reporting ranges are half-open: `[start, end)`.
- Calendar boundaries use a validated IANA timezone.
- Before preferences exist, the frontend sends the browser IANA timezone for dashboard requests.
- The default dashboard range is the previous seven local calendar days including today.
- Dashboard requests are capped at 366 local days; larger analytics jobs require a separate export/reporting design.
- Sessions spanning a reporting boundary are clipped to the range and split across daily buckets.
- Active sessions are shown separately and excluded from completed totals.
- Daylight-saving transitions follow the selected timezone rather than assuming every day is 24 hours.

## 5. Authentication and Security

1. Supabase authenticates the user and returns an access token.
2. The API client attaches `Authorization: Bearer <token>`.
3. Spring validates signature, issuer, expiry/not-before, expected audience, and the accepted asymmetric signing algorithms.
4. Spring rejects a missing or non-UUID `sub` as `401`, before controller logic.
5. Controllers pass only the authenticated UUID to services; services use ownership-scoped repository methods.

Production requirements:

- No permissive HS256 fallback in the production profile.
- CORS allows only configured frontend origins and required methods/headers.
- Secrets are injected by the runtime and never stored in committed `.env` files.
- All public traffic and JDBC connections use TLS.
- Authentication failures return `401`; authenticated ownership failures use `404` to avoid resource enumeration; authorization-policy failures use `403`.
- Notes and other rich content are schema-validated and rendered from trusted document nodes, not arbitrary executable HTML.
- Rate limiting is required before public production launch on authentication-adjacent and mutation endpoints.

## 6. Core API Contract

**Base path:** `/api/v1`
**Content type:** `application/json`
**Timestamps:** ISO-8601 UTC strings

### Endpoints

| Method and path | Auth | Behavior |
|---|---:|---|
| `GET /health` | No | Liveness response; must not require the database |
| `POST /time-entries/start` | Yes | Starts one session; returns `201`; concurrent/duplicate active start returns `409` |
| `GET /time-entries/active` | Yes | Returns the active owned entry or `null` |
| `PUT /time-entries/{id}/stop` | Yes | Stops the owned entry; repeated calls return the unchanged stopped resource |
| `GET /dashboard/stats` | Yes | Returns personal totals and daily buckets for a validated range/timezone |

The frontend and API use `PUT /time-entries/{id}/stop`; the former active-stop compatibility endpoint has been retired after the ID-based path became the only caller contract.

Dashboard requests require `timeZone=<IANA identifier>`. Optional `start` and `end` query parameters are paired ISO local dates, with `end` exclusive; omitting both selects the previous seven local calendar days including today. The response `range.start` and `range.end` are the corresponding UTC instants. Ranges must contain 1–366 local days.

### Core shapes

```ts
type ActivityType = "WORK" | "ROT";

type TimeEntry = {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string | null;
  durationSeconds: number | null;
  notes: string | null;
};

type DashboardStats = {
  range: { start: string; end: string; timeZone: string };
  totalSeconds: Record<ActivityType, number>;
  daily: Array<{ localDate: string; workSeconds: number; rotSeconds: number }>;
  recentSessions: TimeEntry[];
  productivityScore: number;
};
```

`productivityScore` is `WORK / (WORK + ROT) * 100`, rounded to the nearest integer, and is `0` when no completed time exists.

### Error shape

```json
{
  "error": {
    "code": "ACTIVE_SESSION_EXISTS",
    "message": "An active session already exists",
    "fieldErrors": {}
  },
  "timestamp": "2026-08-01T12:00:00Z",
  "path": "/api/v1/time-entries/start"
}
```

Framework authentication, JSON parsing, validation, domain conflicts, missing resources, and unexpected failures must use this shape. Internal details and stack traces are never returned.

## 7. Frontend Architecture

- Public routes: landing, sign-in, sign-up, confirmation.
- Protected routes: tracker, dashboard, and future settings, notes/logs, friends, and groups.
- After sign-in, redirect to the originally requested protected route when present; otherwise redirect to `/dashboard`.
- `AuthProvider` owns the Supabase browser session. The API client obtains/refreshes its bearer token and normalizes errors.
- `useTimeTracking` owns tracker orchestration. Timer display is client-derived, but the API remains authoritative.
- There is no unload or visibility auto-stop hook.
- Interactive application controls use shadcn primitives. Third-party chart/editor internals may use their required APIs while honoring rotrack design tokens and accessibility.
- Loading, empty, error, and retry states are explicit on every data-driven route.

## 8. Future Product Systems

Future systems are separate vertical slices. Their migrations, API contracts, UI, authorization, and tests ship together.

### 8.1 Preferences prerequisite

`user_preferences` stores:

- `user_id` as the unique owner key
- `timezone` as a validated IANA identifier
- optional daily Work goal
- `share_study_summary BOOLEAN NOT NULL DEFAULT false`
- `share_active_study_status BOOLEAN NOT NULL DEFAULT false`
- audit timestamps

All sharing is opt-in and private by default.

### 8.2 WYSIWYG notes

Use Tiptap StarterKit in a client-only editor embedded beside the timer. Persist versioned Tiptap/ProseMirror JSON rather than arbitrary HTML.

`notes` contains:

- `id`, `user_id`, optional `time_entry_id`
- title, limited to 120 characters
- versioned `content_json`, limited to 256 KiB serialized
- derived `content_text` for search and previews
- `content_schema_version`
- optimistic-lock `version`
- audit timestamps

Behavior:

- A note is a standalone document that can optionally link to one session.
- A session can have multiple notes.
- A database constraint/trigger enforces that a linked session has the same owner as the note; deleting a session detaches the note with `ON DELETE SET NULL`.
- Initial formatting: headings, paragraphs, bold, italic, lists, block quotes, links, undo, and redo.
- Autosave after approximately 750 ms of inactivity and display `Saving`, `Saved`, and `Conflict` states.
- The API validates document structure, supported nodes/marks, link protocols, and size.
- Notes remain private and never appear in social or group projections.

### 8.3 Daily study logs

One daily log exists per user and local calendar date.

- Timer totals, timeline, sessions, and linked-note references are generated from authoritative time entries.
- The user may add a private rich-text reflection stored as versioned document JSON.
- Generated statistics cannot be manually edited or duplicated into a mutable summary table.
- Calendar boundaries use the user's saved IANA timezone.
- Changing timezone re-buckets generated study statistics; an existing reflection remains attached to the local-date label under which the user created it.
- The UI provides daily and calendar views.

### 8.4 Friendships and presence

Friendships are mutual and use `PENDING` and `ACCEPTED` states. Store one canonical ordered user pair, the requester, and enforce pair uniqueness. Store blocks separately as directional `(blocker_id, blocked_id)` records so a user can block someone without an existing friendship.

- Friend discovery uses a unique case-normalized public handle. Search requires at least three characters, returns a small rate-limited result set, and never searches or exposes email addresses.
- Users cannot friend themselves.
- Duplicate and reversed requests are rejected.
- Blocking deletes pending/accepted friendship state and pairwise invitations, prevents new direct interaction, and suppresses pairwise activity/presence projections.
- Shared study summaries contain only opted-in Work totals and study-day streak. Goal progress may be added only after the goals feature defines its own sharing contract.
- Rot totals, raw sessions, timestamps, notes, and reflections are never shared.
- Active presence is a derived boolean: `studying=true` only when an opted-in user has an active `WORK` session.
- Social screens poll an authenticated presence projection every 30 seconds while visible and stop polling when hidden or unmounted.

### 8.5 Study groups

Groups are private and invitation-only.

- Roles: `OWNER`, `ADMIN`, `MEMBER`.
- Invitations have explicit pending/accepted/declined/cancelled/expired lifecycle state, with at most one live invitation per group/invitee.
- Owners update/archive the group, manage members/admins, and transfer ownership.
- Admins invite and remove members but cannot remove the owner or transfer ownership.
- Initial invitations can be sent only to accepted friends.
- Ending a friendship or blocking does not silently rewrite third-party group membership or ownership. A block suppresses direct interaction and pairwise activity/presence inside the group; the minimum membership/role metadata needed for group administration remains visible.
- Group views show opted-in Work summaries, aggregate Work totals/streaks, and boolean active-study presence.
- Backend services produce explicit privacy-safe projections. Database entities are never serialized directly.

### Future API areas

- `/friends` and `/friend-requests`: list, request, accept, decline, cancel, remove, block, unblock
- `/social/summaries` and `/social/presence`: privacy-filtered friend/group projections
- `/groups`, `/groups/{id}/invitations`, `/groups/{id}/members`, `/groups/{id}/summary`
- `/notes`: owned CRUD with session/date filters and optimistic version checks
- `/daily-logs/{localDate}`: generated study data plus owned reflection updates

Detailed OpenAPI schemas are written immediately before each vertical slice and become the executable contract for that slice. Frontend DTO types are generated from OpenAPI; the authenticated native-`fetch` wrapper remains hand-written.

## 9. Quality, Delivery, and Operations

### Required tests by boundary

- Domain/service: one-active-session rule, idempotent stop, ownership, range clipping, bucket splitting, DST behavior
- Controller/security: JWT claims, `401/403/404/409`, validation, stable errors
- Database: constraints, indexes, migrations, signup trigger, and two-user RLS behavior for the Data API boundary
- Frontend: tracker restoration, explicit stop, dashboard states, note autosave/conflict, privacy controls
- Browser: sign-up/sign-in, start/reload/stop/dashboard; later note/log and social/group critical flows
- Privacy: shared projections never contain Rot, notes, reflections, or raw sessions

Tests are delivered with each feature; testing is not a cleanup phase.

### CI and deployment

- Pull requests run frontend lint/typecheck/tests/build, backend tests/package, migration checks, and secret scanning.
- Staging deploys the frontend to Vercel and the containerized backend to ECS Fargate against a separate Supabase project.
- Production promotion requires staging smoke evidence and a documented rollback.
- Apply database migrations before application versions that depend on them; migrations must be backward-compatible during rollout.

### Observability

- Structured backend logs include request/correlation ID, route, status, latency, and safe user/resource identifiers.
- Never log bearer tokens, note content, reflections, or credentials.
- Monitor API error rate/latency, ECS health, database connections, migration status, and frontend exceptions.
- Health endpoints distinguish liveness from dependency readiness before ECS rollout.

## 10. Architecture Change Rules

- `arch.plan.md` owns product invariants, architecture, trust boundaries, and contracts.
- `todo.md` owns ordering, status, dependencies, acceptance criteria, and evidence.
- A task is not verified merely because code exists.
- Changes to sharing, timer lifecycle, trust boundaries, or stored rich-text formats require an explicit architecture update and migration/compatibility plan.
- Unresolved alternatives belong in a dated decision task, not as “A or B” instructions inside executable work.
