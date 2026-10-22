# Backend integration tests

The default Maven suite is credential-free. `PostgresMigrationIntegrationTest` and the database-backed service suites are skipped unless the caller explicitly passes
`-Drotrack.postgres.integration=true`. `StudySummaryPostgresIntegrationTest` exercises M6.2 projection behavior against real PostgreSQL and requires an already-migrated disposable loopback database in `verify` mode.

## PostgreSQL migration verification

Use only a disposable, isolated PostgreSQL database or isolated Supabase test
project. The test never prints connection values and rejects JDBC URLs that
contain a password. Supply credentials separately:

```bash
export ROTRACK_TEST_DATABASE_URL='jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require'
export ROTRACK_TEST_DATABASE_USERNAME='TEST_ROLE'
export ROTRACK_TEST_DATABASE_PASSWORD='REDACTED'
export ROTRACK_TEST_DATABASE_ISOLATED='true'

cd backend
```

Choose one target and mode:

- `apply` requires an empty disposable database. It creates a minimal
  `auth.users`/`auth.uid()` test contract only when PostgreSQL does not already
  provide one, applies checked-in migrations `001` through `008`, proves the
  invariants, and rolls back all DDL and data. Run only the raw migration test,
  because the rollback intentionally leaves no schema for a later Spring context:

  ```bash
  ROTRACK_TEST_DATABASE_MODE=apply mvn -Drotrack.postgres.integration=true \
    -Dtest=PostgresMigrationIntegrationTest test
  ```

- `verify` requires an already-migrated isolated database. It checks the actual
  `public.time_entries` catalog, runs rollback-only data probes, and exercises
  the real Spring Data repository against that schema:

  ```bash
  ROTRACK_TEST_DATABASE_MODE=verify mvn -Drotrack.postgres.integration=true \
    -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest,NoteServicePostgresIntegrationTest,DailyLogServicePostgresIntegrationTest,FriendshipStoragePostgresIntegrationTest,FriendshipServicePostgresIntegrationTest,StudySummaryPostgresIntegrationTest,StudyPresencePostgresIntegrationTest' test
  ```

`StudyPresencePostgresIntegrationTest` exercises the one-boolean M6.3 projection with runtime-equivalent grants against the same disposable loopback boundary. It covers active Work versus active Rot/completed Work/no session, both pending/block directions, opt-out/missing preferences, removed friendship, self/missing/unrelated peers, and independent sharing preferences. It removes all fixtures and its probe role afterward.

`StudySummaryPostgresIntegrationTest` invokes the real summary service/repository against disposable migrated PostgreSQL. Its fixture coverage includes accepted/opt-in authorization, unavailable equivalence, date-boundary clipping, future/active/ROT exclusion, fractional-second rounding, midnight, DST and fractional-offset days, streak anchoring/gaps/capping, and a bounded 366-day projection. Fixtures are deleted in teardown. Do not point it at a browser fixture or coordinator database.

`NoteServicePostgresIntegrationTest` uses the public Note service/repository seam to verify
non-transactional reads, exactly-one concurrent creation, replay conflicts/deleted replays,
attachment move/detach and Time Entry deletion, history attachment counts, stale versions,
and ownership. It is intentionally verify-mode only because it needs a committed migrated
schema.

`FriendshipStoragePostgresIntegrationTest` verifies canonical/unique pairs, request membership/state constraints, directional blocks, account-deletion cascades, defensive RLS, immutable database-owned creation timestamps, and browser/runtime column privileges. It permits only an explicitly isolated loopback target with migrations 001–008 already applied. Probe data, grants, and test-role creation roll back. It does not verify lifecycle API behavior or the UI.

`FriendshipServicePostgresIntegrationTest` covers owned transitions, retries, literal prefix search, keyset pages, block cleanup, reversed requests, request/accept-versus-block races, and reciprocal blocks. Deterministic advisory-lock waiter tests prove post-lock re-reads. This suite also requires an explicitly isolated loopback database. It creates a temporary BYPASSRLS probe role that inherits only migration-owned runtime DML plus profile ID/username reads. The role reproduces the API's separate runtime-role setup without widening timestamp grants. The suite removes its committed fixtures and probe role afterward; its administrator connection needs role-creation permission. Browser acceptance is documented in `frontend/e2e/README.md`.

The role needs enough permission for the selected mode, including temporary
fixture inserts into `auth.users` and `public.users`. The raw migration test and
Spring Data repository test prove that:

- the partial unique index rejects a second active row for one user;
- different users can each have an active row;
- the timestamp range check rejects `end_time <= start_time`;
- a one-hour timestamp range derives 3,600 seconds even when transitional
  `duration_minutes` contains `999`; and
- the reporting index exists on `(user_id, start_time)`;
- migration 005 fails closed with diagnostic notes/overlap preflight errors and explicitly installs/checks `btree_gist`;
- the real JPA service update commits an owned edit, rejects another user's edit, and preserves `ACTIVE_SESSION_EXISTS` for a second start;
- usernames are non-null, canonical, regex-validated, reserved-name protected,
  unique, and immutable;
- RLS is enabled with the seven named ownership policies, and the isolated PostgreSQL probe verifies two-user preference read/update/insert isolation;
- the hardened signup trigger reads raw metadata, creates canonical
  rollback-only fixture profiles, and rejects invalid/reserved/duplicate names; and
- `TimeEntryRepository.saveAndFlush` reaches those real PostgreSQL constraints and owner-scoped active reads; the JPA integration test also exercises the transactional service update and two users' preferences; and
- the Notes catalog probe verifies API-only browser access, ownership/link constraints, and exact `rotrack_runtime` grants for Notes and creation replay metadata.

When integration is enabled, missing or unsafe configuration fails instead of
silently skipping. Catalog and rollback-only trigger checks do not prove the
HTTP Data API RLS matrix, real Supabase Auth signup, production grants/bypass,
or Spring's live two-user authorization boundary.

See `database/verification/README.md` for a redacted evidence template.
