package com.rotrack.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Optional PostgreSQL proof for the migration's hardening invariants.
 *
 * This test is deliberately opt-in: it never invents credentials or connects to
 * the configured application database. Set ROTRACK_TEST_DATABASE_URL to an
 * isolated development database before running it.
 */
class PostgresMigrationIntegrationTest {

    @Test
    void appliedDatabaseContainsTheMigrationIndexes() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT indexname, indexdef
                     FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND indexname IN (
                           'idx_time_entries_one_active_per_user',
                           'idx_time_entries_user_start_time'
                       )
                     ORDER BY indexname
                     """)) {
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder indexes = new StringBuilder();
                while (result.next()) {
                    indexes.append(result.getString("indexname"))
                            .append(" ")
                            .append(result.getString("indexdef"))
                            .append("\n");
                }
                String definitions = indexes.toString();
                assertTrue(definitions.contains("idx_time_entries_one_active_per_user"));
                assertTrue(definitions.contains("WHERE (end_time IS NULL)"));
                assertTrue(definitions.contains("idx_time_entries_user_start_time"));
            }
        }
    }

    @Test
    void postgresRejectsDuplicateActiveRowsAndInvalidRanges() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("""
                    CREATE TEMP TABLE rotrack_constraint_probe (
                        user_id uuid NOT NULL,
                        start_time timestamptz NOT NULL,
                        end_time timestamptz,
                        CONSTRAINT probe_end_after_start
                            CHECK (end_time IS NULL OR end_time > start_time)
                    ) ON COMMIT DROP
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX probe_one_active_per_user
                      ON rotrack_constraint_probe(user_id)
                      WHERE end_time IS NULL
                    """);

            UUID userId = UUID.randomUUID();
            statement.executeUpdate(
                    "INSERT INTO rotrack_constraint_probe(user_id, start_time) VALUES ('"
                            + userId + "', now())"
            );
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO rotrack_constraint_probe(user_id, start_time) VALUES ('"
                            + userId + "', now())"
            ));

            UUID otherUserId = UUID.randomUUID();
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO rotrack_constraint_probe(user_id, start_time, end_time) VALUES ('"
                            + otherUserId + "', now(), now() - interval '1 second')"
            ));
            connection.rollback();
        }
    }

    private Connection openConnection() throws SQLException {
        String url = System.getenv("ROTRACK_TEST_DATABASE_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set ROTRACK_TEST_DATABASE_URL to run PostgreSQL integration tests");
        String username = System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_PASSWORD", "");
        return DriverManager.getConnection(url, username, password);
    }
}
