# rotrack Architecture

**Status:** Living architecture and contract document
**Last reviewed:** 2026-08-09
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
- [`frontend/DESIGN.md`](frontend/DESIGN.md) is the visual UI source of truth; product/domain behavior remains governed by this architecture. The checked-in frontend uses local Figtree for display/body text and Digital-7 only for timer-style readouts

### Backend and database

- Spring Boot 3.4, Java 21 target, Maven, Spring Web, JPA, OAuth2 Resource Server, validation, and PostgreSQL
- Implemented endpoints: independent liveness/readiness, start session, get active session, ID-based stop, and dashboard stats
- Supabase migrations defining `users`, `time_entries`, the `ROT|WORK` enum, ownership RLS policies, signup profile creation, timestamp constraints, one-active-session uniqueness, and reporting indexes
- Source-controlled suites include executable PostgreSQL migration/repository tests, generated signed-JWT filter tests, service/controller ownership tests, Vitest/RTL coverage, and a quarantined external-auth Playwright critical path that can bind observed browser API responses to an approved API base
- Backend startup rejects PostgreSQL TLS override properties, requires managed JDBC hostname verification with an explicit CA path, validates exact CORS origins, binds bounded Hikari settings, and exposes cached/single-flight database readiness
- A digest-pinned Java 21 multi-stage OCI-compatible image runs as UID/GID `10001:10001`; local validation covers read-only-root compatibility, probes, runtime CA injection, bounded shutdown, and a platform-neutral artifact boundary. Non-production registry media/digest/architecture and ACA digest/service-version readback are observed. ACA read-only-root enforcement and production artifact evidence remain target work. The checked-in ECS/Fargate templates are historical and unselected
- Pull-request workflows and local guards cover frontend/backend suites, isolated PostgreSQL migration apply/verify, prospective-tree and history secret scanning, operational contract tests, and a credential-free container build. The checked-in authenticated workflow targets logical `nonproduction`, uses the approved two-project topology, runs only from trusted-default-branch `repository_dispatch`, and is disabled before runner/environment/secret access unless an administrator-controlled variable explicitly enables it. Under the approved solo-maintainer policy, GitHub environment secrets remain empty and authenticated E2E uses local external storage states until a second trusted reviewer is available
- The checked-in AWS staging render/validation files are historical and unselected; they still enforce obsolete three-reference/AWS assumptions. The active adapter under `deploy/azure/` and `scripts/azure/` uses one shared non-production Supabase identity, one Vercel project with Preview, logical GitHub gates, and separate Azure boundaries. Non-production cloud configuration/readback is recorded; production and GitHub protection are not
- Release, rollback, monitoring, structured-logging, and incident-response contracts plus fail-closed non-production smoke/rehearsal scripts are tracked. The backend now has a bounded process-local per-user mutation limiter and an allowlisted structured request-completion logger; legacy staging templates fail unless that logger is enabled with staging metadata bound to the immutable image digest. These source controls do not provision a fleet-wide/authentication-adjacent edge limiter, collector redaction, telemetry, alerts, contacts, or infrastructure

### Verification boundaries at the current checkpoint

- The PostgreSQL verification suite has rollback-only evidence against the configured development schema and empty-database apply evidence against isolated temporary PostgreSQL; direct Data API RLS is recorded as verified.
- Spring JDBC does not propagate the user JWT into PostgreSQL. The dedicated `rotrack_runtime` role bypasses RLS with only the required application DML, and the recorded live two-user Spring ownership matrix passes.
- Generated ES256/RS256 failure tests cover the production decoder/filter boundary, while recorded live Supabase sign-in and two-user authenticated ownership flows pass. On 2026-08-09 the product owner/operator attested that the already-confirmed fresh disposable user completed first sign-in and reached `/dashboard`; this is manual acceptance evidence rather than an automated authenticated-suite result.
- The Playwright harness has recorded external two-user auth evidence. Managed-CA startup/readiness/CORS and the integrated non-root container are locally verified, but those local results are not deployed non-production evidence. For the 2026-08-09 local acceptance run, `http://localhost:3001` received the exact allowed CORS origin while `http://localhost:3000` was denied; this origin-specific local configuration does not change the deployed Preview CORS evidence.
- Hosted CI and branch protection are read back on the now-public repository. `main` requires pull requests with zero human approvals and strict app-bound checks (`Guards and secret scan`, `Frontend`, `Backend`, `Backend container artifact`, `PostgreSQL migrations`, `Analyze (actions)`, `Analyze (java-kotlin)`, and `Analyze (javascript-typescript)`), applies to administrators, requires linear history, blocks force pushes/deletion, and keeps `CODEOWNERS` advisory. The `nonproduction` environment allows only protected `main`; repository/non-production/production authentication secret inventories are empty and `ROTRACK_AUTHENTICATED_E2E_ENABLED` is absent/default-disabled. Hosted authenticated deployed smoke, deliberate required-check blocking evidence, fleet-wide/authentication-adjacent edge rate limiting, observed structured-log ingestion and collector redaction, telemetry and alert routing, named incident staffing, cold-start trials, backup/restore safeguards, and rollback rehearsal remain open. The authorized immutable non-production Azure/Vercel deployment itself is observed. M4 stays gated until the M3 MVP release gate is verified.

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

### Approved deployment architecture

This table defines the product-owner-approved topology. The observed non-production checkpoint is recorded in Section 2 and [`docs/operations/azure-nonproduction.md`](docs/operations/azure-nonproduction.md); every production cell remains a target until separately authorized and verified.

| Boundary | Non-production | Production |
|---|---|---|
| Supabase | Existing non-production/dev Supabase Free project, shared by development and approved environment-scoped authenticated E2E | Newly created `rotrack-prod` Supabase Free project |
| Vercel | Preview environment in one Vercel project | Production environment in that same Vercel project |
| GitHub | Target logical `nonproduction` environment for deployment approvals/secrets | Target logical `production` environment for deployment approvals/secrets |
| Azure managed environment | Target `rotrack-nonproduction-env` | Target `rotrack-production-env` |
| Azure resource group / app | Target `rotrack-nonproduction` / `rotrack-api-nonproduction` | Target `rotrack-production` / `rotrack-api-production` |
| Scaling | Azure Container Apps Consumption, minimum replicas `0`; measure at least 10 wake-ups | Minimum replicas `0` only after explicit acceptance when non-production p95 readiness is ≤30 seconds and no trial exceeds 60 seconds; otherwise `1` |

The shared non-production Supabase project is an accepted development/approved environment-scoped E2E tradeoff. Credential-free pull-request CI uses isolated disposable PostgreSQL and never connects to the hosted Supabase project; approved authenticated E2E may use disposable users/data in the shared non-production project, never `rotrack-prod`. The single Vercel project uses Vercel's built-in Preview and Production environments rather than a dedicated staging Vercel project. The Azure managed environments are the security boundary and each is inside its matching separate resource group; separate Container Apps are also required. GitHub/ACA `nonproduction` maps to the application's existing runtime/telemetry label `staging`; production maps to `production`.

The product-owner-approved solo-maintainer equivalent does not claim two-person review. The now-public repository has the approved protection: `main` requires pull requests with zero human approvals, strict app-bound automated checks, administrator enforcement, linear history, and no force pushes/deletion; `CODEOWNERS` remains advisory. Required checks are the five Pull request CI jobs plus default CodeQL setup's `Analyze (actions)`, `Analyze (java-kotlin)`, and `Analyze (javascript-typescript)` contexts. The `nonproduction` environment allows exactly protected `main` with no reviewers or secrets. Repository, `nonproduction`, and `production` authentication secret inventories are empty, and `ROTRACK_AUTHENTICATED_E2E_ENABLED` is absent/default-disabled. Vulnerability reporting, Dependabot security fixes, secret scanning, and push protection are enabled. The hosted authenticated workflow remains disabled; run the authenticated non-production suite from a trusted local operator context with disposable users and external storage states. A future hosted credentialed workflow requires a second trusted reviewer plus environment approval before its administrator-controlled enable variable or secrets may be configured. Read back the available controls against the [GitHub Environments documentation](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). Production deployment remains stopped until the remaining M3 gates pass. The non-production Azure/Vercel resources described in Section 2 are observed; production resources and configuration remain untouched.

The backend image is a platform-neutral OCI-compatible artifact: Linux amd64, immutable registry digest, non-root UID/GID `10001:10001`, writes limited to `/tmp`, port `8080`, process liveness and database readiness probes, graceful shutdown, and runtime CA injection. The current builder can emit Docker media type, so registry manifest media type and digest require deployment readback. The image is locally read-only-root compatible; ACA enforcement is not claimed, so the target requires non-root execution, no remote debug shell, least-privilege managed identity, and isolated secret injection until a supported provider control is verified. If a registry integration must be selected, prefer Azure Container Registry (ACR) with managed identity for Azure pulls; that is an Azure deployment integration, not a change to artifact portability. Consumption scale-to-zero requires the measured acceptance gate in the table plus budget/credit-expiry alerts before production promotion. Azure budget alerts are notifications, not a hard spending cap, and cost/credit-expiry data can be delayed.

### Supabase Free-plan operational constraints

Both target Supabase projects are Free-plan projects. According to the official [Free project pausing documentation](https://supabase.com/docs/guides/platform/free-project-pausing), a low-activity Free project may be automatically paused after a 7-day low-activity period; the owner must monitor pause warnings and own resume/recovery. Supabase's [database backup documentation](https://supabase.com/docs/guides/platform/backups) identifies automatic daily backups as a Pro/Team/Enterprise feature and recommends regular exports for Free projects, so this topology requires encrypted, access-controlled off-site logical exports using [`supabase db dump`](https://supabase.com/docs/reference/cli/supabase-db-dump). PITR is not part of this Free topology.

No backup, pause alert, resume procedure, export retention, or restore evidence is claimed as configured. Before production promotion, complete a restore rehearsal from a retained logical export or record a separate explicit product-owner data-loss risk acceptance.

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
| CI | GitHub Actions with credential-free pull-request jobs; trusted-operator authenticated E2E uses disposable external state, while the hosted credentialed job remains administrator-disabled under the solo-maintainer policy |
| Container | Digest-pinned multi-stage Java 21 OCI-compatible image, non-root UID/GID `10001:10001` |
| Hosting | One Vercel project (Preview for non-production, Production for production), Azure Container Apps Consumption backend, two Supabase Free projects |

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
| `GET /health` | No | Liveness response `200 {"status":"ok"}`; must not require the database |
| `GET /readiness` | No | Database readiness; returns sanitized `200 {"status":"ready"}` or `503 {"status":"not_ready"}` |
| `POST /time-entries/start` | Yes | Starts one session; returns `201`; concurrent/duplicate active start returns `409` |
| `GET /time-entries/active` | Yes | Returns the active owned entry or `null` |
| `PUT /time-entries/{id}/stop` | Yes | Stops the owned entry; repeated calls return the unchanged stopped resource |
| `GET /dashboard/stats` | Yes | Returns personal totals and daily buckets for a validated range/timezone |

The frontend and API use `PUT /time-entries/{id}/stop`; the former active-stop compatibility endpoint has been retired after the ID-based path became the only caller contract. Liveness is process-only. Readiness performs a bounded database validation, caches/single-flights the result to limit pool pressure, and never returns dependency or credential details.

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

- The tracked pull-request workflow runs frontend install/high audit/lint/typecheck/tests/build, backend Java 21 clean tests/package, isolated PostgreSQL migration apply/verify, secret and workflow-policy guards, operational contract tests, and a credential-free non-root container build/inspection.
- GitHub-hosted success, branch protection, and protected-environment restrictions are now externally observed. A deliberately blocking required-check failure remains untested, so M3.1 stays **Implemented—unverified** until that separate failure case is demonstrated.
- The non-production target is the Preview environment of one Vercel project and an immutable-digest Spring backend in the non-production Azure Container App, using the shared non-production Supabase Free project. On 2026-08-09 the authorized non-production Azure/Vercel boundary was created and read back: managed identity ACR pull, digest-bound service version, HTTPS health/readiness, scale `0..1`, budget/log caps, and exact Preview CORS passed. Production remains the same Vercel project's Production environment and a separate production Azure Container App/resource group against `rotrack-prod`; no production mutation was authorized or performed. Source-controlled templates or synthetic validation alone are never cloud evidence.
- The backend promotes the exact tested image digest. The frontend cannot promote the same Preview bytes because `NEXT_PUBLIC_*` environment values are embedded at build time; Vercel Production must build the same reviewed source commit with production-scoped values, record separate immutable deployment provenance, and pass production-safe verification.
- Apply database migrations before application versions that depend on them; migrations must be backward-compatible during rollout. Application rollback never implies automatic database rollback.
- Production promotion requires the tested non-production backend digest, matching source-commit provenance for the environment-specific frontend build, redacted smoke evidence, active safeguards/observability, budget/credit-expiry alerting, and a rehearsed application rollback.
- Consumption scale-to-zero is initially accepted. Runbooks must account for cold-start latency and distinguish a cold start from an outage before paging.

### Observability and release safeguards

- Required structured backend logs include request/correlation ID, route template, status, latency, and only explicitly allowlisted identifiers. The backend implements this application boundary with generated request IDs, normalized route allowlisting, stable error/exception categories, and omission-based redaction; staging ingestion and collector-side redaction remain unobserved.
- Never log bearer tokens, authorization/cookie values, JDBC URLs, credentials, note content, reflections, or private request/response bodies.
- Production monitoring must cover API health/error rate/latency, Container App restarts and replica readiness, authentication failures, database connection exhaustion, migration status, frontend exceptions, Azure budget/credit-expiry risk, cold-start behavior, Supabase Free pause warnings/resume ownership, and logical-backup freshness/restore readiness with environment separation, bounded retention, owners, and tested routing. Azure budget alerts are notifications rather than a hard spending cap, and cost/credit-expiry data can be delayed.
- Rate limiting is required before public production launch on authentication-adjacent and mutation endpoints, including tested `429`, bypass resistance, and recovery behavior. The process-local authenticated mutation limiter is defense in depth only; a trusted fleet-wide/authentication-adjacent edge control remains required.
- Health endpoints distinguish liveness from dependency readiness before Container Apps rollout.
- Release, rollback, monitoring, structured-log redaction, and incident-response contracts are source controlled, but they are preparation rather than evidence that telemetry, alerts, owners, staging smoke, or rollback rehearsal are active.

### MVP release boundary

The M3 MVP release gate is currently **not met**. Production promotion and M4 implementation remain stopped until the backlog records all prerequisite milestone gates as **Verified**, hosted CI/protection evidence, an authorized non-production backend deployment at an immutable registry digest, environment-specific frontend build provenance, active rate limiting/logging/telemetry/alerts with owners, authenticated non-production smoke, and an exact backend candidate-to-prior rollback rehearsal. Any exception requires an explicit product-owner dependency decision in both architecture and backlog.

## 10. Architecture Change Rules

- `arch.plan.md` owns product invariants, architecture, trust boundaries, and contracts.
- `todo.md` owns ordering, status, dependencies, acceptance criteria, and evidence.
- A task is not verified merely because code exists.
- Changes to sharing, timer lifecycle, trust boundaries, or stored rich-text formats require an explicit architecture update and migration/compatibility plan.
- Unresolved alternatives belong in a dated decision task, not as “A or B” instructions inside executable work.
