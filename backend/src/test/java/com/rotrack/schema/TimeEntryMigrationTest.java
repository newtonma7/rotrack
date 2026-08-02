package com.rotrack.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TimeEntryMigrationTest {

    @Test
    void enforcesOneActiveSessionPerUserAndSupportsUserTimelineQueries() throws IOException {
        String migration = Files.readString(findMigration());

        assertTrue(migration.contains("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_time_entries_one_active_per_user
                  ON public.time_entries(user_id)
                  WHERE end_time IS NULL;
                """));
        assertTrue(migration.contains("""
                CREATE INDEX IF NOT EXISTS idx_time_entries_user_start_time
                  ON public.time_entries(user_id, start_time);
                """));
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("database/migrations/002_harden_time_entries.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate 002_harden_time_entries.sql");
    }
}
