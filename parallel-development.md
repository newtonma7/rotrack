# Parallel Development Guide

**Purpose:** Coordinate independent work after M1.4 without conflicting edits or advancing a milestone gate before its evidence exists.

**Source of truth:** Read [`AGENTS.md`](AGENTS.md), [`arch.plan.md`](arch.plan.md), and [`todo.md`](todo.md) first. This file is an execution guide; it does not replace their contracts or acceptance criteria.

**Current baseline:** M1.4 — Correct dashboard time semantics is implemented and recorded as **Verified**. M1.1, M1.2, and M1.5 are not yet verified. M2.1–M2.3 are in progress. The M1.4 implementation is currently an uncommitted worktree change; do not reset, clean, or rewrite it.

## Coordination rules

1. One writer owns each lane and its files. Do not edit another lane's files.
2. Start every lane by inspecting `git status`, the current diff, `arch.plan.md`, and the relevant `todo.md` task.
3. Preserve unrelated dirty work. Never use `git reset --hard`, broad checkout, or cleanup commands in the shared worktree.
4. Prefer a separate worktree for each implementation writer. Read-only reviewers may inspect the shared checkout.
5. Every lane must add or update tests with behavior changes and return exact commands, results, changed files, and residual risks.
6. Do not mark a task **Verified** from source existence or unit tests alone when its acceptance requires PostgreSQL, Supabase, real JWTs, two users, or browser evidence.
7. Do not record tokens, credentials, database passwords, private notes, or complete environment files in evidence.

## Worktree execution with Herdr

Herdr panes and Git worktrees solve different parts of the workflow: a Herdr pane provides a visible project session, while a managed Git worktree provides filesystem and branch isolation. Use both when interactive inspection is useful, but do not treat opening a pane as isolation by itself.

### Recommended setup

1. Let the M1.4 writer finish and review its dashboard changes.
2. Create a clean checkpoint branch/commit containing the accepted M1.4 work, the documentation updates, and this coordination guide. Do not create worktrees from the current dirty checkout.
3. Create one managed worktree per implementation lane:
   - `m1.1-db-verification`
   - `m1.2-security-verification`
   - `m1.5-playwright`
   - optionally `m2.2-startup`
4. Give each subagent exactly one lane, an explicit worktree, and the file ownership listed below. M2.1 is primarily an operational/read-only Supabase lane and may not need a code worktree.
5. Open a Herdr project pane rooted at a lane worktree only when the lane benefits from interactive supervision or browser work.
6. Let each writer run its focused tests and produce a handoff. The parent reviews each diff and merges/cherry-picks lanes serially into the integration branch.
7. Re-run the full frontend/backend suites after integration. Update `todo.md` only after the parent reviews the combined result.

### Worktree safety rules

- Never run `git reset --hard`, `git clean`, or broad checkout commands in the shared M1.4 checkout.
- Do not allow two writers to use the same worktree or edit the same migration, API contract, README, architecture, or backlog file concurrently.
- Keep `todo.md`, `arch.plan.md`, and final status/evidence updates owned by the parent unless a lane is explicitly assigned a documentation change.
- A worktree is not proof of correctness; tests, database probes, browser evidence, and parent review are still required.
- If the base checkout is dirty, finish or checkpoint the active work first rather than stashing files while another writer is running.

## Immediate parallel lanes

These lanes can proceed concurrently. They have intentionally different ownership boundaries.

### Lane A — M1.1 database-backed migration verification

**Task:** Close the remaining M1.1 gap.

**Owns:** `backend/src/test/java/com/rotrack/schema/`, new repository integration-test support, and redacted migration evidence. Avoid dashboard and JWT source files.

**Do:**

- Apply `database/migrations/001_initial_schema.sql` and `002_harden_time_entries.sql` to an isolated PostgreSQL test database or approved development database.
- Prove duplicate active inserts fail for one user, active inserts succeed for different users, invalid ranges fail, and timestamp-derived duration ignores transitional `duration_minutes`.
- Keep the test repeatable and free of production credentials.
- Re-run/record the development catalog and migration-version checks if the environment is available.

**Done when:** The repository contains executable database-backed coverage and the evidence required by M1.1 is redacted and repeatable.

### Lane B — M1.2 JWT and ownership verification

**Task:** Close the remaining M1.2 gap.

**Owns:** `backend/src/test/java/com/rotrack/config/`, `backend/src/test/java/com/rotrack/controller/`, security-test support, and integration evidence. Avoid dashboard implementation files.

**Do:**

- Add signed-token tests for wrong signature and unsupported algorithm, in addition to issuer, audience, expiry/not-before, and UUID-subject cases.
- Verify stable `401` envelopes for authentication failures.
- Verify User B receives `404` when reading or stopping User A's entry; do not rely only on a mocked service.
- Use generated test keys or isolated test credentials; never commit real tokens.

**Done when:** M1.2 acceptance cases have automated coverage and the real-token/two-user evidence is recorded without secrets.

### Lane C — M1.5 Playwright and test-suite completion

**Task:** Complete the remaining test infrastructure without duplicating Lane A or B.

**Owns:** `frontend/playwright.config.*`, `frontend/tests/` or `frontend/e2e/`, auth setup documentation, and CI-test command documentation.

**Do:**

- Add the Playwright skeleton for sign-in, start, reload/restore, explicit stop, and dashboard navigation.
- Document how external Supabase authentication is provisioned or safely stubbed in non-production tests.
- Add only missing frontend coverage; M1.4 already covers dashboard semantics, query encoding, and loading/empty/error/retry states.

**Done when:** The Playwright setup is tracked, credentials are externalized, and the critical-path command is reproducible or explicitly quarantined with an owner and reason.

### Lane D — M2.1 Supabase migration, RLS, and role evidence

**Task:** Advance M2.1 while M1 verification proceeds.

**Owns:** Supabase development environment operations, migration/version evidence, and the two-user RLS/grant matrix. Do not change migration SQL unless an architecture decision is approved.

**Do:**

- Confirm migrations and the signup trigger are applied.
- Test Data API RLS independently for two users.
- Verify the Spring application role's required DML grants, TLS connection settings, and documented RLS-bypass model.
- Verify Spring ownership-scoped queries separately from browser/Data API RLS.

**Done when:** The redacted migration/version and two-user isolation matrix satisfy M2.1. This lane may run concurrently, but the M2 gate remains closed until all M1 tasks are verified.

### Lane E — M2.2 deterministic local startup

**Task:** Complete local startup evidence.

**Owns:** `README.md`, `.env.example` files, Spring configuration, CORS/startup documentation, and readiness/liveness tests. Coordinate before editing docs also touched by another lane.

**Do:**

- Start backend and frontend from the runbook using Java 21 and pinned Node.
- Verify `/api/v1/health` independently from database readiness.
- Verify configured CORS, issuer/JWKS/audience, and TLS JDBC settings without recording values.
- Add a separate readiness contract if the implementation needs dependency health for deployment.

**Done when:** Clean-start commands, status codes/bodies, and a configuration-name checklist are recorded.

## Integration order

```text
M1.1 database lane ─┐
M1.2 security lane ─┼─> M1.5 test completion ─┐
M2.1 Supabase lane ─┤                         ├─> M2.3 two-user critical path
M2.2 startup lane ──┘                         │
                                              └─> M1 milestone acceptance review
```

M2.3 must wait for the corrected M1.4 dashboard contract (now complete), M2.1 environment evidence, M2.2 startup evidence, and the required M1 verification. It must cover sign-up/sign-in, Work and Rot, reload/close restoration, explicit stop, dashboard totals, and User A/User B ownership isolation.

## Later parallel opportunities

### After the MVP release gate

- **M4.1 history/manual corrections** and **M4.2 preferences** can be implemented in parallel if their migrations and API files are isolated.
- **M4.3 OpenAPI generation** should follow the implemented M4 APIs, not run ahead of unstable contracts.

### After M4.2 preferences and the MVP

- **M5.1 notes data/API** and **M6.1 friendship/blocking** can proceed in parallel; they have separate tables, services, controllers, and UI.
- **M5.2 editor** waits for M5.1's notes contract.
- **M5.3 daily logs** waits for the notes/reflection contract and timezone preferences.
- **M6.2 privacy summaries** and **M6.3 presence** can proceed in parallel after M6.1 and the preference contract.

### Groups

- **M7.1 group model/invitations** must come first.
- **M7.2 group UI** and **M7.3 summaries/presence** can proceed in parallel after M7.1's membership and role contracts stabilize.

## Release/operations work

M3.1 CI can be prepared once the frontend/backend commands and migration checks are stable. M3.2 staging and M3.3 release safeguards should remain sequential after the M2 gate: CI, then staging, then smoke/rollback/observability evidence.

## Parent acceptance checklist

Before accepting a lane:

- [ ] Changed files are within the lane boundary.
- [ ] Tests cover success, failure, and authorization/privacy cases relevant to the lane.
- [ ] Exact commands and results are recorded.
- [ ] No secrets or private content appear in evidence.
- [ ] `todo.md` status/evidence is updated only after the parent reviews the diff.
- [ ] `git diff --check` passes.
- [ ] The parent has reviewed cross-lane contract changes before merging them.
