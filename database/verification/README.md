# Database verification evidence

Run the PostgreSQL-backed tests with the environment contract documented in
`backend/src/test/README.md`. Never paste populated environment variables into
an evidence record.

## Empty-database migration application

Use `apply` only on an empty disposable PostgreSQL database. The migration test
applies the checked-in SQL, verifies it, and then rolls back the entire schema.
The repository test must not be included because no schema remains after that
rollback.

```text
Date (UTC): YYYY-MM-DD
Target: empty disposable PostgreSQL
Mode: apply
Migration inputs: database/migrations/001_initial_schema.sql,
                  database/migrations/002_harden_time_entries.sql,
                  database/migrations/003_require_usernames.sql,
                  database/migrations/004_user_preferences.sql,
                  database/migrations/005_time_entry_history.sql
Command: cd backend && ROTRACK_TEST_DATABASE_MODE=apply \
         mvn -Drotrack.postgres.integration=true \
         -Dtest=PostgresMigrationIntegrationTest test
Result: PASS — tests run: 2, failures: 0, errors: 0, skipped: 0
Proved: checked-in migrations execute in order; actual catalog objects; enabled
        application-table RLS and named policies; rollback-only signup trigger;
        canonical username normalization; invalid/reserved/duplicate signup
        rejection; immutable usernames; fail-closed existing-profile probe;
        same-user active rejection (23505); different-user active rows;
        invalid range rejection (23514); 3,600 timestamp-derived seconds with
        duration_minutes=999
Cleanup: transaction rolled back, including DDL
Credentials/host/database identifiers: REDACTED
```

## Already-migrated database and repository verification

Use `verify` on an isolated database where migrations 001 through 005 are
already applied and existing profiles have been prepared with valid unique
usernames. Both test classes run and roll back their probe rows.

```text
Date (UTC): YYYY-MM-DD
Target: isolated migrated PostgreSQL | isolated Supabase test project
Mode: verify
Migration inputs: database/migrations/001_initial_schema.sql,
                  database/migrations/002_harden_time_entries.sql,
                  database/migrations/003_require_usernames.sql,
                  database/migrations/004_user_preferences.sql,
                  database/migrations/005_time_entry_history.sql
Command: cd backend && ROTRACK_TEST_DATABASE_MODE=verify \
         mvn -Drotrack.postgres.integration=true \
         -Dtest='PostgresMigrationIntegrationTest,TimeEntryRepositoryPostgresIntegrationTest' test
Result: PASS — tests run: 5, failures: 0, errors: 0, skipped: 1 (the apply-only fail-closed probe)
Proved: actual application-table catalog, enabled RLS/named policies, and
        rollback-only signup trigger; canonical username normalization,
        validation, uniqueness, and immutability; same-user active rejection
        (23505); different-user active rows; invalid range rejection (23514);
        same-user overlap exclusion (23P01), adjacent ranges, 280-character
        notes ceiling; 3,600 timestamp-derived seconds with duration_minutes=999;
        Spring Data flushes and owner-scoped active reads against the migrated schema
Cleanup: probe transactions rolled back
Credentials/host/database identifiers: REDACTED
Limitations: does not prove HTTP Data API RLS, real Supabase Auth signup,
             production grants/bypass, or Spring two-user authorization;
             repository verification deliberately
             isolates schema behavior from the separate startup TLS guard
```

For independent review, retain the Maven test summary and PostgreSQL major
version. Redact hostnames, database names, usernames, connection strings, and
all secret values. A skipped default-suite result is not database verification.
