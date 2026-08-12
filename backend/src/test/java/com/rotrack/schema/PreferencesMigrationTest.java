package com.rotrack.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreferencesMigrationTest {

    @Test
    void createsPrivateOneRowPerUserPreferencesWithSafeDefaults() throws IOException {
        String migration = Files.readString(findMigration());

        assertThat(migration).contains("CREATE TABLE public.user_preferences");
        assertThat(migration).contains("user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE");
        assertThat(migration).contains("timezone TEXT");
        assertThat(migration).contains("daily_work_goal_minutes INTEGER");
        assertThat(migration).contains("CHECK (daily_work_goal_minutes IS NULL OR daily_work_goal_minutes BETWEEN 1 AND 1440)");
        assertThat(migration).contains("share_study_summary BOOLEAN NOT NULL DEFAULT false");
        assertThat(migration).contains("share_active_study_status BOOLEAN NOT NULL DEFAULT false");
        assertThat(migration).contains("ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY");
        assertThat(migration).contains("user_preferences_select_own");
        assertThat(migration).contains("user_preferences_insert_own");
        assertThat(migration).contains("user_preferences_update_own");
        assertThat(migration).contains("name = 'UTC'");
        assertThat(migration).contains("name LIKE '%/%'");
        assertThat(migration).contains("timezone must be a valid IANA identifier");
        assertThat(migration).doesNotContain("SELECT 1 FROM pg_timezone_names WHERE name = NEW.timezone");
        assertThat(migration).contains("auth.uid() = user_id");
        assertThat(migration).contains("REVOKE ALL PRIVILEGES ON TABLE public.user_preferences FROM rotrack_runtime");
        assertThat(migration).contains("GRANT SELECT, INSERT, UPDATE ON TABLE public.user_preferences TO rotrack_runtime");
        assertThat(migration).doesNotContain("userId");
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("database/migrations/004_user_preferences.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate 004_user_preferences.sql");
    }
}
