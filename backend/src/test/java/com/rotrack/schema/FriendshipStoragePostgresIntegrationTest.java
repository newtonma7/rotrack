package com.rotrack.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Source/local storage proof only; lifecycle and HTTP ownership remain separate M6.1 work. */
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
class FriendshipStoragePostgresIntegrationTest {
    // These straddle Java UUID's signed comparison boundary. SQL canonical ordering is unsigned.
    private static final UUID LOW = UUID.fromString("10000000-0061-4000-8000-000000000001");
    private static final UUID HIGH = UUID.fromString("90000000-0061-4000-8000-000000000002");
    private static final UUID THIRD = UUID.fromString("f0000000-0061-4000-8000-000000000003");
    private Connection connection;

    @BeforeEach
    void connectToDisposableLocalDatabase() throws Exception {
        Assumptions.assumeTrue("verify".equals(System.getenv("ROTRACK_TEST_DATABASE_MODE")),
                "This storage suite requires applied migrations; apply mode rolls its schema back");
        if (!"true".equalsIgnoreCase(System.getenv("ROTRACK_TEST_DATABASE_ISOLATED"))) {
            throw new IllegalStateException("An explicitly isolated PostgreSQL target is required");
        }
        String url = System.getenv("ROTRACK_TEST_DATABASE_URL");
        if (url == null || !url.startsWith("jdbc:postgresql://") || url.contains("password=")) {
            throw new IllegalStateException("Use a PostgreSQL JDBC URL and a separate password variable");
        }
        String host = URI.create(url.substring("jdbc:".length())).getHost();
        if (!List.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host)) {
            throw new IllegalStateException("M6.1 foundation verification permits loopback targets only");
        }
        connection = DriverManager.getConnection(url,
                System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_USERNAME", "postgres"),
                System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_PASSWORD", ""));
        connection.setAutoCommit(false);
        for (UUID user : List.of(LOW, HIGH, THIRD)) {
            update("INSERT INTO auth.users(id, email, raw_user_meta_data) VALUES (?, ?, jsonb_build_object('username', ?))",
                    user, "fixture-" + user + "@example.test", "m61_" + user.toString().substring(0, 8));
        }
    }

    @AfterEach
    void rollBackAllProbeDataAndGrants() throws Exception {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void canonicalPairIsUniqueAndRequesterAndStateAreConstrained() throws Exception {
        UUID relationship = insertFriendship(LOW, HIGH, HIGH, "PENDING");
        assertEquals(1, count("SELECT count(*) FROM public.friendships WHERE id = ?", relationship));
        expectSqlState("23505", () -> insertFriendship(LOW, HIGH, LOW, "PENDING"));
        expectSqlState("23514", () -> insertFriendship(HIGH, LOW, LOW, "PENDING"));
        expectSqlState("23514", () -> insertFriendship(LOW, LOW, LOW, "PENDING"));
        expectSqlState("23514", () -> insertFriendship(LOW, THIRD, HIGH, "PENDING"));
        expectSqlState("23514", () -> insertFriendship(LOW, THIRD, LOW, "DECLINED"));
        expectSqlState("23502", () -> insertFriendship(LOW, THIRD, LOW, null));
        update("UPDATE public.friendships SET state = 'ACCEPTED' WHERE id = ?", relationship);
        assertEquals(1, count("SELECT count(*) FROM public.friendships WHERE id = ? AND state = 'ACCEPTED'", relationship));
    }

    @Test
    void blocksAreIndependentDirectionalRecordsWithNoSelfOrDuplicateBlock() throws Exception {
        insertBlock(LOW, HIGH);
        insertBlock(HIGH, LOW);
        assertEquals(2, count("SELECT count(*) FROM public.user_blocks"));
        expectSqlState("23505", () -> insertBlock(LOW, HIGH));
        expectSqlState("23514", () -> insertBlock(LOW, LOW));
        expectSqlState("23503", () -> insertBlock(LOW, UUID.randomUUID()));
        update("DELETE FROM public.user_blocks WHERE blocker_id = ? AND blocked_id = ?", LOW, HIGH);
        assertEquals(1, count("SELECT count(*) FROM public.user_blocks WHERE blocker_id = ? AND blocked_id = ?", HIGH, LOW));
    }

    @Test
    void accountDeletionCascadesBothPairPositionsAndBlockDirections() throws Exception {
        insertFriendship(LOW, HIGH, LOW, "PENDING");
        insertFriendship(HIGH, THIRD, THIRD, "ACCEPTED");
        insertBlock(LOW, HIGH);
        insertBlock(HIGH, LOW);
        insertBlock(THIRD, HIGH);
        update("DELETE FROM auth.users WHERE id = ?", HIGH);
        assertEquals(0, count("SELECT count(*) FROM public.friendships"));
        assertEquals(0, count("SELECT count(*) FROM public.user_blocks"));
        assertEquals(2, count("SELECT count(*) FROM public.users WHERE id IN (?, ?)", LOW, THIRD));
    }

    @Test
    void defensiveRlsExposesOnlyParticipantRelationshipsAndOwnOutgoingBlocks() throws Exception {
        insertFriendship(LOW, HIGH, LOW, "PENDING");
        insertFriendship(HIGH, THIRD, HIGH, "ACCEPTED");
        insertBlock(LOW, HIGH);
        insertBlock(HIGH, LOW);
        execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_m61_rls_probe') THEN
                    CREATE ROLE rotrack_m61_rls_probe NOLOGIN NOSUPERUSER NOBYPASSRLS;
                  END IF;
                END $$;
                GRANT USAGE ON SCHEMA public, auth TO rotrack_m61_rls_probe;
                GRANT SELECT, INSERT, UPDATE, DELETE ON public.friendships, public.user_blocks TO rotrack_m61_rls_probe;
                SET LOCAL ROLE rotrack_m61_rls_probe;
                """);
        subject(LOW);
        assertEquals(1, count("SELECT count(*) FROM public.friendships"));
        assertEquals(1, count("SELECT count(*) FROM public.user_blocks"));
        assertEquals(0, count("SELECT count(*) FROM public.user_blocks WHERE blocker_id = ?", HIGH));
        expectSqlState("42501", () -> insertBlock(LOW, THIRD));
        assertEquals(0, update("DELETE FROM public.user_blocks WHERE blocker_id = ?", LOW));
        subject(HIGH);
        assertEquals(2, count("SELECT count(*) FROM public.friendships"));
        assertEquals(1, count("SELECT count(*) FROM public.user_blocks"));
        subject(THIRD);
        assertEquals(1, count("SELECT count(*) FROM public.friendships"));
        assertEquals(0, count("SELECT count(*) FROM public.user_blocks"));
        execute("RESET ROLE");
    }

    @Test
    void browserRolesHaveNoTableAccessAndRuntimeGrantsAreDmlOnly() throws Exception {
        for (String role : List.of("anon", "authenticated")) {
            // Local CI does not supply Supabase's roles. Create unprivileged probes if absent.
            execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + role
                    + "') THEN CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOBYPASSRLS; END IF; END $$");
            for (String table : List.of("public.friendships", "public.user_blocks")) {
                assertFalse(privilege(role, table, "SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER"));
            }
        }
        for (String table : List.of("public.friendships", "public.user_blocks")) {
            for (String access : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                assertTrue(privilege("rotrack_runtime", table, access));
                assertFalse(privilege("rotrack_runtime", table, access + " WITH GRANT OPTION"));
            }
            assertFalse(privilege("rotrack_runtime", table, "TRUNCATE, REFERENCES, TRIGGER"));
            assertEquals(1, count("SELECT count(*) FROM pg_class WHERE oid = ?::regclass AND relrowsecurity", table));
        }
    }

    private UUID insertFriendship(UUID low, UUID high, UUID requester, String state) throws SQLException {
        UUID id = UUID.randomUUID();
        update("INSERT INTO public.friendships(id, user_low_id, user_high_id, requester_id, state) VALUES (?, ?, ?, ?, ?)",
                id, low, high, requester, state);
        return id;
    }

    private void insertBlock(UUID blocker, UUID blocked) throws SQLException {
        update("INSERT INTO public.user_blocks(blocker_id, blocked_id) VALUES (?, ?)", blocker, blocked);
    }

    private void subject(UUID user) throws SQLException {
        try (PreparedStatement statement = statement("SELECT set_config('request.jwt.claim.sub', ?, true)", user.toString())) {
            statement.execute();
        }
    }

    private boolean privilege(String role, String table, String access) throws SQLException {
        return count("SELECT CASE WHEN has_table_privilege(?, ?, ?) THEN 1 ELSE 0 END", role, table, access) == 1;
    }

    private int count(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = statement(sql, parameters); var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private int update(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = statement(sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    private PreparedStatement statement(String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
        return statement;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void expectSqlState(String state, SqlOperation operation) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            SQLException failure = assertThrows(SQLException.class, operation::run);
            assertEquals(state, failure.getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
