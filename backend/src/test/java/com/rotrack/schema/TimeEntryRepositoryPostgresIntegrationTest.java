package com.rotrack.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.dto.CompletedTimeEntryRequest;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.model.UserPreferences;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.repository.UserPreferencesRepository;
import com.rotrack.service.TimeEntryService;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
        "SUPABASE_JWT_AUDIENCE=authenticated",
        "rotrack.notes.hmac-secret=test-only-notes-hmac-secret-32-bytes"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
@Transactional
class TimeEntryRepositoryPostgresIntegrationTest {

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    @Autowired
    private TimeEntryRepository repository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private TimeEntryService timeEntryService;

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
    void serviceStartConflictStaysActiveSessionExists() {
        assertThat(timeEntryService.startSession(USER_A, ActivityType.WORK, null).endTime()).isNull();

        assertThatThrownBy(() -> timeEntryService.startSession(USER_A, ActivityType.ROT, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active session already exists");
    }

    @Test
    void serviceUpdateCommitsOwnedChangeAndRejectsAnotherUser() {
        TimeEntry original = repository.saveAndFlush(entry(
                USER_A,
                Instant.parse("2026-08-07T10:00:00Z"),
                Instant.parse("2026-08-07T11:00:00Z")));
        CompletedTimeEntryRequest request = new CompletedTimeEntryRequest(
                ActivityType.ROT,
                Instant.parse("2026-08-07T12:00:01Z"),
                Instant.parse("2026-08-07T12:30:02Z"),
                "updated");

        assertThat(timeEntryService.updateCompletedEntry(USER_A, original.getId(), request).notes())
                .isEqualTo("updated");
        assertThat(repository.findByIdAndUserId(original.getId(), USER_A).orElseThrow().getActivityType())
                .isEqualTo(ActivityType.ROT);
        assertThat(repository.findByIdAndUserId(original.getId(), USER_A).orElseThrow().getStartTime())
                .isEqualTo(request.startTime());
        assertThatThrownBy(() -> timeEntryService.updateCompletedEntry(USER_B, original.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void twoUsersCanPersistAndReadOwnedPreferencesThroughJpa() {
        UserPreferences first = preferencesRepository.findById(USER_A).orElseThrow();
        first.setTimezone("America/New_York");
        first.setDailyWorkGoalMinutes(60);
        preferencesRepository.saveAndFlush(first);

        UserPreferences second = preferencesRepository.findById(USER_B).orElseThrow();
        second.setTimezone("Europe/Berlin");
        second.setDailyWorkGoalMinutes(90);
        preferencesRepository.saveAndFlush(second);

        assertThat(preferencesRepository.findById(USER_A).orElseThrow().getTimezone())
                .isEqualTo("America/New_York");
        assertThat(preferencesRepository.findById(USER_B).orElseThrow().getTimezone())
                .isEqualTo("Europe/Berlin");
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

    @Test
    void sameUserOverlapIsRejectedButAdjacentCompletedRangesAreValid() {
        Instant start = Instant.parse("2026-08-07T10:00:00Z");
        TimeEntry first = entry(USER_A, start, start.plus(Duration.ofHours(1)));
        repository.saveAndFlush(first);

        assertThat(repository.existsOverlappingEntry(
                USER_A, start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(90)), null)).isTrue();
        assertThat(repository.existsOverlappingEntry(
                USER_A, start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(2)), null)).isFalse();
        assertThat(repository.existsOverlappingEntry(
                USER_A, start, start.plus(Duration.ofHours(1)), first.getId())).isFalse();

        repository.saveAndFlush(entry(USER_A, start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(2))));

        assertThatThrownBy(() -> repository.saveAndFlush(
                entry(USER_A, start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(90)))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completedHistoryOrdersByStartThenIdAndExcludesActiveRows() {
        Instant start = Instant.parse("2026-08-07T10:00:00Z");
        TimeEntry first = entry(USER_A, start, start.plus(Duration.ofHours(1)));
        TimeEntry second = entry(USER_A, start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(3)));
        repository.saveAndFlush(first);
        repository.saveAndFlush(second);
        repository.saveAndFlush(entry(USER_A, start.plus(Duration.ofHours(4)), null));

        assertThat(repository.findCompletedHistory(USER_A, PageRequest.of(0, 20)))
                .extracting(TimeEntry::getId)
                .containsExactly(second.getId(), first.getId());
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM public.users WHERE id = ?", String.class, userId
        )).isEqualTo(username);
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
