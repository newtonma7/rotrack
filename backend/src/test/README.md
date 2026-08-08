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
  provide one, applies checked-in migrations `001` then `002`, proves the
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
    -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest' test
  ```

The role needs enough permission for the selected mode, including temporary
fixture inserts into `auth.users` and `public.users`. The raw migration test and
Spring Data repository test prove that:

- the partial unique index rejects a second active row for one user;
- different users can each have an active row;
- the timestamp range check rejects `end_time <= start_time`;
- a one-hour timestamp range derives 3,600 seconds even when transitional
  `duration_minutes` contains `999`; and
- the reporting index exists on `(user_id, start_time)`;
- RLS is enabled with the seven named ownership policies;
- the hardened signup trigger creates rollback-only fixture profiles; and
- `TimeEntryRepository.saveAndFlush` reaches those real PostgreSQL constraints and owner-scoped active reads.

When integration is enabled, missing or unsafe configuration fails instead of
silently skipping. Catalog and rollback-only trigger checks do not prove the
HTTP Data API RLS matrix, real Supabase Auth signup, production grants/bypass,
or Spring's live two-user authorization boundary.

See `database/verification/README.md` for a redacted evidence template.
