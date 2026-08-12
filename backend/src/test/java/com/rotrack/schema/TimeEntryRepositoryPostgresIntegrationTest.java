package com.rotrack.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in Spring Data proof against the actual migrated PostgreSQL schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "SUPABASE_JWKS_URI=https://example.test/jwks",
        "SUPABASE_ISSUER_URI=https://example.test/issuer",
        "SUPABASE_JWT_AUDIENCE=authenticated"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
@Transactional
class TimeEntryRepositoryPostgresIntegrationTest {

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    @Autowired
    private TimeEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // This test isolates repository/schema behavior. Startup TLS policy has its own focused tests.
    @MockitoBean
    private DatabaseTlsValidator databaseTlsValidator;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        requireIsolatedTarget();
        registry.add("spring.datasource.url", () -> requiredEnvironment("ROTRACK_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> environmentOrDefault(
                "ROTRACK_TEST_DATABASE_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> environmentOrDefault(
                "ROTRACK_TEST_DATABASE_PASSWORD", ""));
    }

    @BeforeEach
    void createOwnedUsers() {
        insertUser(USER_A);
        insertUser(USER_B);
    }

    @Test
    void differentUsersCanEachPersistAndReadAnActiveSession() {
        repository.saveAndFlush(entry(USER_A, Instant.parse("2026-08-07T10:00:00Z"), null));
        repository.saveAndFlush(entry(USER_B, Instant.parse("2026-08-07T10:01:00Z"), null));

        assertThat(repository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(USER_A)).isPresent();
        assertThat(repository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(USER_B)).isPresent();
    }

    @Test
    void sameUserCannotFlushTwoActiveSessions() {
        repository.saveAndFlush(entry(USER_A, Instant.parse("2026-08-07T10:00:00Z"), null));

        assertThatThrownBy(() -> repository.saveAndFlush(
                entry(USER_A, Instant.parse("2026-08-07T10:01:00Z"), null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidTimestampRangeCannotBeFlushed() {
        assertThatThrownBy(() -> repository.saveAndFlush(entry(
                USER_A,
                Instant.parse("2026-08-07T10:00:00Z"),
                Instant.parse("2026-08-07T09:59:59Z")
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID userId) {
        String email = "rotrack-repository-test-" + userId + "@example.invalid";
        String username = "repo_" + userId.toString().replace("-", "").substring(0, 18);
        jdbcTemplate.update(
                "INSERT INTO auth.users(id, email, raw_user_meta_data) VALUES (?, ?, ?::jsonb)",
                userId,
                email,
                "{\"username\":\"" + username + "\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO public.users(id, email, username)
                VALUES (?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, userId, email, username);
    }

    private TimeEntry entry(UUID userId, Instant start, Instant end) {
        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setCreatedAt(start);
        entry.setUpdatedAt(start);
        return entry;
    }

    private static void requireIsolatedTarget() {
        if (!"true".equalsIgnoreCase(requiredEnvironment("ROTRACK_TEST_DATABASE_ISOLATED"))) {
            throw new IllegalStateException(
                    "ROTRACK_TEST_DATABASE_ISOLATED must be true after confirming the target is disposable");
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when PostgreSQL integration is enabled");
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
