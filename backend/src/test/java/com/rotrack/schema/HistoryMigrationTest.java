package com.rotrack.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoryMigrationTest {

    @Test
    void enforcesShortNotesAndHalfOpenSameUserRangeExclusion() throws IOException {
        String migration = Files.readString(findMigration());

        assertTrue(migration.contains("CREATE EXTENSION IF NOT EXISTS btree_gist;"));
        assertTrue(migration.contains("char_length(notes) <= 280"));
        assertTrue(migration.contains("CONSTRAINT time_entries_no_overlap_per_user"));
        assertTrue(migration.contains("tstzrange(start_time, COALESCE(end_time, 'infinity'::timestamptz), '[)') WITH &&"));
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("database/migrations/005_time_entry_history.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate 005_time_entry_history.sql");
    }
}
