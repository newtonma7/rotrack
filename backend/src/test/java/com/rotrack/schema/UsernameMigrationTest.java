package com.rotrack.schema;

import static org.assertj.core.api.Assertions.assertThat;
import com.rotrack.model.User;
import jakarta.persistence.Column;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameMigrationTest {

    @Test
    void requiresExistingCanonicalUsernamesWithoutRewritingAccounts() throws IOException {
        String migration = Files.readString(findMigration());

        assertThat(migration).contains("ALTER TABLE public.users\n  ALTER COLUMN username SET NOT NULL;");
        assertThat(migration).contains("WHERE username IS NULL");
        assertThat(migration).contains("username = lower(btrim(username))");
        assertThat(migration).contains("^[a-z0-9_]{3,24}$");
        assertThat(migration).contains("users_username_not_reserved");
        for (String reservedName : List.of(
                "admin", "api", "support", "help", "rotrack", "signin", "signup",
                "confirmation", "dashboard", "tracker", "settings")) {
            assertThat(migration).contains("'" + reservedName + "'");
        }
        assertThat(migration).doesNotContainPattern("(?is)\\bUPDATE\\s+public\\.users");
        assertThat(migration).doesNotContainPattern("(?is)\\bDELETE\\s+FROM\\s+public\\.users");
        assertThat(migration).doesNotContainPattern("(?is)\\bDROP\\s+(TABLE|SCHEMA)\\b");
    }

    @Test
    void signupUsesRawMetadataAndKeepsSecurityDefinerSearchPath() throws IOException {
        String migration = Files.readString(findMigration());

        assertThat(migration).contains("NEW.raw_user_meta_data->>'username'");
        assertThat(migration).contains("SECURITY DEFINER");
        assertThat(migration).contains("SET search_path = public");
        assertThat(migration).contains("BEFORE UPDATE OF username ON public.users");
    }

    @Test
    void jpaDeclaresUsernameNonNullable() throws NoSuchFieldException {
        Field username = User.class.getDeclaredField("username");

        assertThat(username.getAnnotation(Column.class)).isNotNull();
        assertThat(username.getAnnotation(Column.class).nullable()).isFalse();
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("database/migrations/003_require_usernames.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate 003_require_usernames.sql");
    }
}
