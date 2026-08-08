# PostgreSQL verification — 2026-08-07

- **Target:** Isolated configured development PostgreSQL (identifiers redacted)
- **Mode:** `verify`
- **Database version observed by Hibernate:** PostgreSQL 17.6
- **Migration inputs:** `database/migrations/001_initial_schema.sql`, `database/migrations/002_harden_time_entries.sql`
- **Command:**

  ```bash
  cd backend
  ROTRACK_TEST_DATABASE_MODE=verify \
    mvn -Drotrack.postgres.integration=true \
    -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest' test
  ```

- **Result:** PASS — 4 tests run, 0 failures, 0 errors, 0 skipped
- **Proved:**
  - actual application-table catalog and reporting/partial indexes;
  - RLS enabled on both application tables with all seven named ownership policies;
  - enabled `auth.users` signup trigger, hardened security-definer function configuration, and rollback-only profile creation for two fixture users;
  - duplicate active sessions for one user fail with SQLSTATE `23505`;
  - different users can each have an active session;
  - invalid timestamp ranges fail with SQLSTATE `23514`;
  - a one-hour timestamp range derives 3,600 seconds even when transitional `duration_minutes` is 999;
  - `TimeEntryRepository.saveAndFlush` reaches the real PostgreSQL constraints and owner-scoped active reads.
- **Cleanup:** Every probe transaction rolled back.
- **Credentials, host, database, role, and generated fixture identifiers:** REDACTED
- **Limitations:** This run does not prove empty-database `apply` mode, the HTTP Data API RLS matrix, real Supabase Auth signup, application-role grants/bypass, live Spring two-user authorization, or managed TLS with the provider CA.
