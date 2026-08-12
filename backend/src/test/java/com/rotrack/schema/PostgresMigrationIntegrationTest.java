package com.rotrack.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in proof that the checked-in migrations enforce the PostgreSQL schema contract.
 *
 * <p>The caller must explicitly confirm that the target is isolated. Every change, including DDL
 * in apply mode, runs in one transaction and is rolled back. This keeps the default Maven suite
 * credential-free and prevents a test invocation from modifying an application database.</p>
 */
class PostgresMigrationIntegrationTest {

    private static final String ENABLE_PROPERTY = "rotrack.postgres.integration";
    private static final String APPLY_MODE = "apply";
    private static final String VERIFY_MODE = "verify";
    private static final List<String> MIGRATIONS = List.of(
            "001_initial_schema.sql",
            "002_harden_time_entries.sql",
            "003_require_usernames.sql",
            "004_user_preferences.sql",
            "005_time_entry_history.sql"
    );

    @Test
    void existingUsernameViolationsMakeMigrationFailWithoutChangingAccounts() throws Exception {
        TestDatabaseConfiguration configuration = configuration();
        Assumptions.assumeTrue(APPLY_MODE.equals(configuration.mode()),
                "the fail-closed apply probe requires an empty target");

        try (Connection connection = DriverManager.getConnection(
                configuration.url(), configuration.username(), configuration.password())) {
            connection.setAutoCommit(false);
            try {
                assertMigrationTargetIsEmpty(connection);
                createMinimalAuthContractWhenNeeded(connection);
                applyMigration(connection, "001_initial_schema.sql");
                applyMigration(connection, "002_harden_time_entries.sql");

                UUID existingUser = UUID.randomUUID();
                insertAuthUser(connection, existingUser, "existing_name");
                assertEquals(1, queryCount(connection, """
                        SELECT count(*) FROM public.users WHERE id = ? AND username IS NULL
                        """, existingUser));

                String usernameMigration = Files.readString(
                        findRepositoryRoot().resolve("database/migrations/003_require_usernames.sql"));
                expectSqlState(connection, "23514", () -> executeSql(connection, usernameMigration));
                assertEquals(1, queryCount(connection, """
                        SELECT count(*) FROM public.users WHERE id = ? AND username IS NULL
                        """, existingUser), "failed migration must not rewrite the existing profile");

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE public.users SET username = ? WHERE id = ?")) {
                    statement.setString(1, "Bad Name");
                    statement.setObject(2, existingUser);
                    statement.executeUpdate();
                }
                expectSqlState(connection, "23514", () -> executeSql(connection, usernameMigration));
                assertEquals("Bad Name", querySingleString(connection, "SELECT username FROM public.users WHERE id = '" + existingUser + "'"));
                assertEquals("YES", querySingleString(connection, """
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'users'
                          AND column_name = 'username'
                        """), "failed migration must not partially set NOT NULL");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void concurrentUsernameClaimsAreResolvedByUniqueConstraint() throws Exception {
        TestDatabaseConfiguration configuration = configuration();
        Assumptions.assumeTrue(VERIFY_MODE.equals(configuration.mode()),
                "the concurrency probe requires a committed migrated schema");

        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection firstConnection = DriverManager.getConnection(
                configuration.url(), configuration.username(), configuration.password());
             Connection secondConnection = DriverManager.getConnection(
                     configuration.url(), configuration.username(), configuration.password())) {
            firstConnection.setAutoCommit(false);
            secondConnection.setAutoCommit(false);

            Future<?> firstClaim = executor.submit(() -> {
                try {
                    insertAuthUser(firstConnection, firstUser, "concurrent_name");
                    firstInserted.countDown();
                    if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to commit the first username claim");
                    }
                    firstConnection.commit();
                    return null;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            assertTrue(firstInserted.await(10, TimeUnit.SECONDS));
            Future<String> secondClaim = executor.submit(() -> {
                try {
                    insertAuthUser(secondConnection, secondUser, "CONCURRENT_NAME");
                    return "success";
                } catch (SQLException exception) {
                    return exception.getSQLState();
                } finally {
                    secondConnection.rollback();
                }
            });

            releaseFirst.countDown();
            firstClaim.get(10, TimeUnit.SECONDS);
            assertEquals("23505", secondClaim.get(10, TimeUnit.SECONDS),
                    "the committed first claim must win the concurrent case-insensitive race");

            try (PreparedStatement statement = firstConnection.prepareStatement(
                    "DELETE FROM auth.users WHERE id IN (?, ?)")) {
                statement.setObject(1, firstUser);
                statement.setObject(2, secondUser);
                statement.executeUpdate();
                firstConnection.commit();
            }
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void actualMigratedSchemaEnforcesDatabaseInvariants() throws Exception {
        TestDatabaseConfiguration configuration = configuration();

        try (Connection connection = DriverManager.getConnection(
                configuration.url(), configuration.username(), configuration.password())) {
            connection.setAutoCommit(false);
            try {
                if (APPLY_MODE.equals(configuration.mode())) {
                    assertMigrationTargetIsEmpty(connection);
                    createMinimalAuthContractWhenNeeded(connection);
                    applyOrderedMigrations(connection);
                }

                assertActualSchemaContract(connection);
                proveTimeEntryInvariants(connection);
                provePreferencesInvariants(connection);
                proveTwoUserPreferencesRls(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    private TestDatabaseConfiguration configuration() {
        String enabled = System.getProperty(ENABLE_PROPERTY, "false").trim();
        if (!enabled.equals("true") && !enabled.equals("false")) {
            throw new IllegalStateException("-D" + ENABLE_PROPERTY + " must be either true or false");
        }
        Assumptions.assumeTrue(Boolean.parseBoolean(enabled),
                "PostgreSQL integration is opt-in; see backend/src/test/README.md");

        String url = requiredEnvironment("ROTRACK_TEST_DATABASE_URL");
        if (url.toLowerCase(Locale.ROOT).contains("password=")) {
            throw new IllegalStateException(
                    "ROTRACK_TEST_DATABASE_URL must not contain a password; use the separate password variable");
        }
        String mode = requiredEnvironment("ROTRACK_TEST_DATABASE_MODE").toLowerCase(Locale.ROOT);
        if (!mode.equals(APPLY_MODE) && !mode.equals(VERIFY_MODE)) {
            throw new IllegalStateException("ROTRACK_TEST_DATABASE_MODE must be apply or verify");
        }
        if (!"true".equalsIgnoreCase(requiredEnvironment("ROTRACK_TEST_DATABASE_ISOLATED"))) {
            throw new IllegalStateException(
                    "ROTRACK_TEST_DATABASE_ISOLATED must be true after confirming the target is disposable");
        }

        return new TestDatabaseConfiguration(
                url,
                environmentOrDefault("ROTRACK_TEST_DATABASE_USERNAME", "postgres"),
                environmentOrDefault("ROTRACK_TEST_DATABASE_PASSWORD", ""),
                mode
        );
    }

    private void assertMigrationTargetIsEmpty(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT to_regclass('public.users')::text,
                       to_regclass('public.time_entries')::text,
                       to_regtype('public.activity_type')::text
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                if (result.getString(1) != null || result.getString(2) != null || result.getString(3) != null) {
                    fail("Apply mode requires an isolated database without the rotrack public schema; "
                            + "use verify mode for an already-migrated isolated database");
                }
            }
        }
    }

    private void createMinimalAuthContractWhenNeeded(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS auth");
            if (!relationExists(connection, "auth.users")) {
                statement.execute("""
                        CREATE TABLE auth.users (
                            id UUID PRIMARY KEY,
                            email TEXT,
                            raw_user_meta_data JSONB NOT NULL DEFAULT '{}'::jsonb
                        )
                        """);
            }
            if (!functionExists(connection, "auth.uid()")) {
                statement.execute("""
                        CREATE FUNCTION auth.uid()
                        RETURNS UUID
                        LANGUAGE sql
                        STABLE
                        AS $$SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid$$
                        """);
            }
            statement.execute("""
                    DO $$
                    BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
                        CREATE ROLE rotrack_runtime NOLOGIN NOSUPERUSER NOBYPASSRLS;
                      END IF;
                    END $$
                    """);
        }
    }

    private void applyOrderedMigrations(Connection connection) throws IOException, SQLException {
        for (String migration : MIGRATIONS) {
            applyMigration(connection, migration);
        }
    }

    private void applyMigration(Connection connection, String migration) throws IOException, SQLException {
        executeSql(connection, Files.readString(findRepositoryRoot().resolve("database/migrations").resolve(migration)));
    }

    private void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertActualSchemaContract(Connection connection) throws SQLException {
        assertTrue(relationExists(connection, "public.users"), "public.users must be an actual table");
        assertTrue(relationExists(connection, "public.time_entries"),
                "public.time_entries must be an actual table");
        assertTrue(relationExists(connection, "public.user_preferences"),
                "public.user_preferences must be an actual table");

        String rangeConstraint = querySingleString(connection, """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'public.time_entries'::regclass
                  AND conname = 'time_entries_end_after_start'
                """);
        assertNotNull(rangeConstraint, "time_entries_end_after_start must exist");
        String normalizedConstraint = normalize(rangeConstraint);
        assertTrue(normalizedConstraint.contains("end_time is null"));
        assertTrue(normalizedConstraint.contains("end_time > start_time"));

        String activeIndex = querySingleString(connection, """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'time_entries'
                  AND indexname = 'idx_time_entries_one_active_per_user'
                """);
        assertNotNull(activeIndex, "the one-active-session index must exist on public.time_entries");
        String normalizedActiveIndex = normalize(activeIndex);
        assertTrue(normalizedActiveIndex.contains("create unique index"));
        assertTrue(normalizedActiveIndex.contains("(user_id)"));
        assertTrue(normalizedActiveIndex.contains("where (end_time is null)"));

        String overlapConstraint = querySingleString(connection, """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'public.time_entries'::regclass
                  AND conname = 'time_entries_no_overlap_per_user'
                """);
        assertNotNull(overlapConstraint, "same-user overlap exclusion must exist");
        String normalizedOverlap = normalize(overlapConstraint);
        assertTrue(normalizedOverlap.contains("exclude using gist"));
        assertTrue(normalizedOverlap.contains("user_id with ="));
        assertTrue(normalizedOverlap.contains("tstzrange"));
        assertTrue(normalizedOverlap.contains("infinity"));
        assertTrue(normalizedOverlap.contains("&&"));
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'public.time_entries'::regclass
                  AND conname = 'time_entries_notes_max_length'
                """));

        String reportingIndex = querySingleString(connection, """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'time_entries'
                  AND indexname = 'idx_time_entries_user_start_time'
                """);
        assertNotNull(reportingIndex, "the reporting index must exist on public.time_entries");
        assertTrue(normalize(reportingIndex).contains("(user_id, start_time)"));

        assertEquals(3, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_class
                WHERE oid IN ('public.users'::regclass, 'public.time_entries'::regclass,
                              'public.user_preferences'::regclass)
                  AND relrowsecurity
                """), "RLS must remain enabled on all application tables");
        assertEquals(10, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_policies
                WHERE schemaname = 'public'
                  AND (
                    (tablename = 'users' AND policyname IN (
                      'users_select_own', 'users_insert_own', 'users_update_own'
                    ))
                    OR
                    (tablename = 'time_entries' AND policyname IN (
                      'time_entries_select_own', 'time_entries_insert_own',
                      'time_entries_update_own', 'time_entries_delete_own'
                    ))
                    OR
                    (tablename = 'user_preferences' AND policyname IN (
                      'user_preferences_select_own', 'user_preferences_insert_own',
                      'user_preferences_update_own'
                    ))
                  )
                """), "all ownership policies must exist");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_policies
                WHERE schemaname = 'public'
                  AND tablename = 'users'
                  AND policyname = 'users_select_own'
                  AND qual = '(auth.uid() = id)'
                """), "username visibility must remain owner-scoped");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_trigger
                WHERE tgrelid = 'auth.users'::regclass
                  AND tgname = 'on_auth_user_created'
                  AND tgfoid = 'public.handle_new_user()'::regprocedure
                  AND NOT tgisinternal
                  AND tgenabled <> 'D'
                """), "the enabled signup trigger must call public.handle_new_user");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_proc
                WHERE oid = 'public.handle_new_user()'::regprocedure
                  AND prosecdef
                  AND 'search_path=public' = ANY (proconfig)
                """), "the signup function must be security-definer with a fixed public search_path");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                  AND column_name = 'username'
                  AND is_nullable = 'NO'
                """), "usernames must be NOT NULL");
        assertEquals(3, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'public.users'::regclass
                  AND conname IN (
                    'users_username_canonical',
                    'users_username_format',
                    'users_username_not_reserved'
                  )
                """), "username canonical, format, and reserved-name checks must exist");
        String signupFunction = querySingleString(connection, "SELECT pg_get_functiondef('public.handle_new_user()'::regprocedure)");
        assertNotNull(signupFunction);
        assertTrue(signupFunction.contains("NEW.raw_user_meta_data->>'username'"),
                "signup must read the username from raw_user_meta_data");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_trigger
                WHERE tgrelid = 'public.user_preferences'::regclass
                  AND tgname = 'user_preferences_timezone_valid'
                  AND NOT tgisinternal
                  AND tgenabled <> 'D'
                """), "saved timezone validation trigger must be enabled");
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'public.user_preferences'::regclass
                  AND conname = 'user_preferences_daily_work_goal_range'
                """), "daily goal range must be database-enforced");
        assertEquals(3, querySingleInt(connection, """
                SELECT count(*)
                FROM aclexplode((SELECT relacl FROM pg_class WHERE oid = 'public.user_preferences'::regclass))
                WHERE grantee = 'rotrack_runtime'::regrole
                  AND privilege_type IN ('SELECT', 'INSERT', 'UPDATE')
                """), "runtime role must have exactly the three preference DML grants");
        assertEquals(0, querySingleInt(connection, """
                SELECT count(*)
                FROM aclexplode((SELECT relacl FROM pg_class WHERE oid = 'public.user_preferences'::regclass))
                WHERE grantee = 'rotrack_runtime'::regrole
                  AND privilege_type NOT IN ('SELECT', 'INSERT', 'UPDATE')
                """), "runtime role must not have other preference table grants");
    }

    private void proveTimeEntryInvariants(Connection connection) throws SQLException {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        insertAuthUser(connection, firstUser, " Alice_One ");
        insertAuthUser(connection, secondUser, "second_user");
        assertEquals(2, queryCount(connection, """
                SELECT count(*)
                FROM public.users
                WHERE id IN (?, ?)
                """, firstUser, secondUser), "the signup trigger must create both public profiles");
        assertEquals("alice_one", querySingleString(connection, "SELECT username FROM public.users WHERE id = '" + firstUser + "'"));
        assertEquals("second_user", querySingleString(connection, "SELECT username FROM public.users WHERE id = '" + secondUser + "'"));

        UUID invalidUser = UUID.randomUUID();
        expectSqlState(connection, "23514", () -> insertAuthUser(connection, invalidUser, "ab"));
        assertEquals(0, queryCount(connection, """
                SELECT count(*) FROM auth.users WHERE id = ?
                """, invalidUser), "invalid signup must not reserve an auth row");

        UUID reservedUser = UUID.randomUUID();
        expectSqlState(connection, "23514", () -> insertAuthUser(connection, reservedUser, " Dashboard "));
        assertEquals(0, queryCount(connection, """
                SELECT count(*) FROM public.users WHERE id = ?
                """, reservedUser), "reserved signup must not create a profile");

        UUID missingMetadataUser = UUID.randomUUID();
        expectSqlState(connection, "23514", () -> insertAuthUserWithoutUsername(connection, missingMetadataUser));
        assertEquals(0, queryCount(connection, """
                SELECT count(*) FROM auth.users WHERE id = ?
                """, missingMetadataUser), "missing metadata must not reserve an auth row");

        UUID duplicateUser = UUID.randomUUID();
        expectSqlState(connection, "23505", () -> insertAuthUser(connection, duplicateUser, "ALICE_ONE"));
        assertEquals(0, queryCount(connection, """
                SELECT count(*) FROM auth.users WHERE id = ?
                """, duplicateUser), "duplicate username must not reserve an auth row");

        expectSqlState(connection, "23514", () -> updateUsername(connection, firstUser, "renamed_user"));
        assertEquals("alice_one", querySingleString(connection, "SELECT username FROM public.users WHERE id = '" + firstUser + "'"));

        OffsetDateTime start = OffsetDateTime.of(2026, 8, 7, 12, 0, 0, 0, ZoneOffset.UTC);
        insertTimeEntry(connection, UUID.randomUUID(), firstUser, start.plusHours(8), null, null);

        expectSqlState(connection, "23505", () ->
                insertTimeEntry(connection, UUID.randomUUID(), firstUser, start.plusHours(8).plusMinutes(1), null, null));
        expectSqlState(connection, "23P01", () ->
                insertTimeEntry(connection, UUID.randomUUID(), firstUser,
                        start.plusHours(8).plusMinutes(1), start.plusHours(8).plusMinutes(2), null));

        insertTimeEntry(connection, UUID.randomUUID(), secondUser, start, null, null);
        assertEquals(2, queryCount(connection, """
                SELECT count(*)
                FROM public.time_entries
                WHERE user_id IN (?, ?)
                  AND end_time IS NULL
                """, firstUser, secondUser), "different users may each have an active row");

        expectSqlState(connection, "23514", () -> insertTimeEntry(
                connection,
                UUID.randomUUID(),
                secondUser,
                start,
                start.minusSeconds(1),
                null
        ));

        UUID completedEntry = UUID.randomUUID();
        insertTimeEntry(connection, completedEntry, firstUser, start.plusHours(2), start.plusHours(3), 999);
        insertTimeEntry(connection, UUID.randomUUID(), firstUser, start.plusHours(3), start.plusHours(4), null);
        expectSqlState(connection, "23P01", () -> insertTimeEntry(
                connection, UUID.randomUUID(), firstUser,
                start.plusHours(2).plusMinutes(1), start.plusHours(2).plusMinutes(2), null));
        expectSqlState(connection, "23514", () -> insertTimeEntry(
                connection, UUID.randomUUID(), firstUser,
                start.plusHours(5), start.plusHours(6), null, "x".repeat(281)));
        expectSqlState(connection, "23514", () -> insertTimeEntry(
                connection, UUID.randomUUID(), firstUser,
                start.plusHours(4), start.plusHours(4), null));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT duration_minutes,
                       CAST(EXTRACT(EPOCH FROM (end_time - start_time)) AS BIGINT)
                FROM public.time_entries
                WHERE id = ?
                """)) {
            statement.setObject(1, completedEntry);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(999, result.getInt(1), "the transitional value intentionally disagrees");
                assertEquals(3_600L, result.getLong(2),
                        "duration must be derived from authoritative timestamps");
            }
        }
    }

    private void insertAuthUser(Connection connection, UUID userId, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO auth.users(id, email, raw_user_meta_data) VALUES (?, ?, ?::jsonb)")) {
            statement.setObject(1, userId);
            statement.setString(2, "rotrack-db-test-" + userId + "@example.invalid");
            statement.setString(3, "{\"username\":\"" + username + "\"}");
            statement.executeUpdate();
        }
    }

    private void insertAuthUserWithoutUsername(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO auth.users(id, email, raw_user_meta_data) VALUES (?, ?, '{}'::jsonb)")) {
            statement.setObject(1, userId);
            statement.setString(2, "rotrack-db-test-" + userId + "@example.invalid");
            statement.executeUpdate();
        }
    }

    private void updateUsername(Connection connection, UUID userId, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE public.users SET username = ? WHERE id = ?")) {
            statement.setString(1, username);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    private void provePreferencesInvariants(Connection connection) throws SQLException {
        UUID userId = UUID.randomUUID();
        insertAuthUser(connection, userId, "preferences_user");

        assertEquals(1, queryCount(connection, """
                SELECT count(*) FROM public.user_preferences WHERE user_id = ?
                """, userId));
        assertEquals(1, querySingleInt(connection, """
                SELECT count(*)
                FROM public.user_preferences
                WHERE user_id = ?
                  AND timezone IS NULL
                  AND daily_work_goal_minutes IS NULL
                  AND share_study_summary = false
                  AND share_active_study_status = false
                """, userId), "new preference rows must be private by default");

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT timezone, daily_work_goal_minutes,
                       share_study_summary, share_active_study_status
                FROM public.user_preferences WHERE user_id = ?
                """)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(null, result.getString(1));
                assertEquals(null, result.getObject(2));
                assertEquals(false, result.getBoolean(3));
                assertEquals(false, result.getBoolean(4));
            }
        }

        expectSqlState(connection, "23505", () -> insertPreference(connection, userId, "UTC", 60));
        expectSqlState(connection, "23514", () -> insertPreference(connection, UUID.randomUUID(), "UTC", 0));
        expectSqlState(connection, "22023", () -> insertPreference(connection, UUID.randomUUID(), "Not/A_Zone", 60));
    }

    private void proveTwoUserPreferencesRls(Connection connection) throws SQLException {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID insertUser = UUID.randomUUID();
        insertAuthUser(connection, firstUser, "rls_first_user");
        insertAuthUser(connection, secondUser, "rls_second_user");
        insertAuthUser(connection, insertUser, "rls_insert_user");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$
                    BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_rls_probe') THEN
                        CREATE ROLE rotrack_rls_probe NOLOGIN NOSUPERUSER NOBYPASSRLS;
                      END IF;
                    END $$
                    """);
            statement.execute("GRANT USAGE ON SCHEMA public TO rotrack_rls_probe");
            statement.execute("GRANT SELECT ON public.users, public.user_preferences TO rotrack_rls_probe");
            statement.execute("GRANT INSERT, UPDATE ON public.user_preferences TO rotrack_rls_probe");
            statement.execute("DELETE FROM public.user_preferences WHERE user_id = '" + insertUser + "'");
            statement.execute("SET LOCAL ROLE rotrack_rls_probe");
        }

        setJwtSubject(connection, firstUser);
        assertEquals(1, queryCount(connection,
                "SELECT count(*) FROM public.user_preferences WHERE user_id IN (?, ?)", firstUser, secondUser),
                "user A can read only user A preferences");
        assertEquals(1, updatePreferenceGoal(connection, firstUser, secondUser, 30),
                "user A can update only user A preferences");
        setJwtSubject(connection, secondUser);
        assertEquals(1, queryCount(connection,
                "SELECT count(*) FROM public.user_preferences WHERE user_id IN (?, ?)", firstUser, secondUser),
                "user B can read only user B preferences");
        assertEquals(1, updatePreferenceGoal(connection, firstUser, secondUser, 45),
                "user B can update only user B preferences");
        setJwtSubject(connection, insertUser);
        insertPreference(connection, insertUser, "Europe/Berlin", 60);
        assertEquals(1, queryCount(connection,
                "SELECT count(*) FROM public.user_preferences WHERE user_id = ? AND timezone = 'Europe/Berlin'",
                insertUser), "owner-scoped insert is allowed");
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET ROLE");
            statement.execute("RESET request.jwt.claim.sub");
        }
    }

    private int updatePreferenceGoal(Connection connection, UUID firstUser, UUID secondUser, int goal)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE public.user_preferences SET daily_work_goal_minutes = ? WHERE user_id IN (?, ?)")) {
            statement.setInt(1, goal);
            statement.setObject(2, firstUser);
            statement.setObject(3, secondUser);
            return statement.executeUpdate();
        }
    }

    private void setJwtSubject(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('request.jwt.claim.sub', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.execute();
        }
    }

    private void insertPreference(Connection connection, UUID userId, String timezone, Integer dailyGoal)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO public.user_preferences(user_id, timezone, daily_work_goal_minutes)
                VALUES (?, ?, ?)
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, timezone);
            statement.setObject(3, dailyGoal);
            statement.executeUpdate();
        }
    }

    private void insertTimeEntry(
            Connection connection,
            UUID id,
            UUID userId,
            OffsetDateTime start,
            OffsetDateTime end,
            Integer durationMinutes
    ) throws SQLException {
        insertTimeEntry(connection, id, userId, start, end, durationMinutes, null);
    }

    private void insertTimeEntry(
            Connection connection,
            UUID id,
            UUID userId,
            OffsetDateTime start,
            OffsetDateTime end,
            Integer durationMinutes,
            String notes
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO public.time_entries(
                    id, user_id, activity_type, start_time, end_time, duration_minutes, notes
                ) VALUES (?, ?, 'WORK', ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, start);
            statement.setObject(4, end);
            statement.setObject(5, durationMinutes);
            statement.setString(6, notes);
            statement.executeUpdate();
        }
    }

    private void expectSqlState(Connection connection, String expectedState, SqlOperation operation)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            operation.run();
            fail("Expected PostgreSQL SQLSTATE " + expectedState);
        } catch (SQLException exception) {
            assertEquals(expectedState, exception.getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private boolean relationExists(Connection connection, String qualifiedName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, qualifiedName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private boolean functionExists(Connection connection, String qualifiedSignature) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regprocedure(?) IS NOT NULL")) {
            statement.setString(1, qualifiedSignature);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private String querySingleString(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private int querySingleInt(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private int queryCount(Connection connection, String sql, UUID first, UUID second) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, first);
            statement.setObject(2, second);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private int queryCount(Connection connection, String sql, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private int querySingleInt(Connection connection, String sql, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("database/migrations"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate database/migrations");
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when PostgreSQL integration is enabled");
        }
        return value.trim();
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private record TestDatabaseConfiguration(String url, String username, String password, String mode) {
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
