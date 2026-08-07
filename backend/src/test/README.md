# Backend integration tests

The default Maven suite is safe to run without credentials. PostgreSQL-backed
migration checks are opt-in and use an isolated development database supplied
by the caller:

```bash
export ROTRACK_TEST_DATABASE_URL='jdbc:postgresql://...'
export ROTRACK_TEST_DATABASE_USERNAME='...'
export ROTRACK_TEST_DATABASE_PASSWORD='...'
cd backend
mvn -Dtest=PostgresMigrationIntegrationTest test
```

The test does not print connection values. It verifies the applied hardening
indexes and exercises equivalent PostgreSQL partial-unique/check constraints
inside a transaction-scoped temporary table. It does not prove Supabase RLS,
signup triggers, or two-user application authorization; those remain separate
remote integration evidence.
