package com.rotrack.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotesMigrationTest {
    @Test
    void migrationIsAdditiveAndKeepsRichContentApiOnly() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/006_notes.sql"));
        assertThat(sql).contains("CREATE TABLE public.notes", "content_json JSON NOT NULL")
                .contains("FOREIGN KEY (attachment_owner_id, time_entry_id)")
                .contains("ON DELETE SET NULL")
                .contains("CREATE TABLE public.note_creation_replays")
                .doesNotContain("REFERENCES public.notes")
                .contains("REVOKE ALL PRIVILEGES ON TABLE public.notes, public.note_creation_replays FROM anon")
                .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.notes TO rotrack_runtime")
                .contains("GRANT SELECT, INSERT, UPDATE ON TABLE public.note_creation_replays TO rotrack_runtime")
                .contains("REVOKE ALL PRIVILEGES ON TABLE public.notes, public.note_creation_replays FROM authenticated");
    }

    @Test
    void migrationRequiresJsonEnvelopeVersionToMatchColumnVersion() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/006_notes.sql"));
        assertThat(sql).contains("notes_content_json_schema_version_match")
                .contains("content_json -> 'schemaVersion'")
                .contains("content_schema_version");
    }

    @Test
    void migrationLeavesRichTreeValidationToSpring() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/006_notes.sql"));
        assertThat(sql).contains("content_json JSON NOT NULL")
                .doesNotContain("taskList", "taskItem", "jsonb_path_exists", "json_schema_valid");
    }

    @Test
    void migrationDoesNotTouchSessionLabels() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/006_notes.sql"));
        assertThat(sql).doesNotContain("DROP COLUMN notes", "ALTER COLUMN notes", "UPDATE public.time_entries");
        assertThat(sql).contains("notes_time_entry_owner_fk", "deleted_version", "PRIMARY KEY (owner_id, idempotency_key)");
    }
}
