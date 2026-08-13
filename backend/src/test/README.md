# Backend integration tests

The default Maven suite is credential-free. `PostgresMigrationIntegrationTest`
is skipped unless the caller explicitly passes
`-Drotrack.postgres.integration=true`.

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
  provide one, applies checked-in migrations `001` through `006`, proves the
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
    -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest,NoteServicePostgresIntegrationTest' test
  ```

`NoteServicePostgresIntegrationTest` uses the public Note service/repository seam to verify
non-transactional reads, exactly-one concurrent creation, replay conflicts/deleted replays,
attachment move/detach and Time Entry deletion, history attachment counts, stale versions,
and ownership. It is intentionally verify-mode only because it needs a committed migrated
schema.

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
