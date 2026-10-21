package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.dto.UpdateReflectionRequest;
import com.rotrack.exception.ConflictException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.Note;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "SUPABASE_JWKS_URI=https://example.test/jwks",
        "SUPABASE_ISSUER_URI=https://example.test/issuer",
        "SUPABASE_JWT_AUDIENCE=authenticated",
        "rotrack.notes.hmac-secret=test-only-notes-hmac-secret-32-bytes"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
class DailyLogServicePostgresIntegrationTest {
    private static final UUID OWNER = UUID.fromString("31111111-1111-4111-8111-111111111111");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private DailyLogService service;
    @Autowired private TimeEntryRepository entries;
    @Autowired private NoteRepository notes;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DatabaseTlsValidator databaseTlsValidator;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (!"true".equalsIgnoreCase(System.getenv("ROTRACK_TEST_DATABASE_ISOLATED"))) {
            throw new IllegalStateException("PostgreSQL integration requires an explicitly isolated target");
        }
        registry.add("spring.datasource.url", () -> System.getenv("ROTRACK_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_PASSWORD", ""));
    }

    @BeforeEach
    void createOwner() {
        jdbc.update("DELETE FROM auth.users WHERE id = ?", OWNER);
        jdbc.update("INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES (?, ?, jsonb_build_object('username', ?))",
                OWNER, "daily-log@example.test", "daily_log_" + OWNER.toString().substring(0, 8));
    }

    @AfterEach
    void removeOwner() {
        jdbc.update("DELETE FROM auth.users WHERE id = ?", OWNER);
    }

    private UpdateReflectionRequest request(com.fasterxml.jackson.databind.JsonNode content, long version) {
        UpdateReflectionRequest request = new UpdateReflectionRequest();
        request.setContentJson(content);
        request.setExpectedVersion(version);
        return request;
    }

    @Test
    void postgresProjectionAndReflectionReplayPreserveContract() throws Exception {
        TimeEntry entry = new TimeEntry();
        entry.setUserId(OWNER);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(Instant.parse("2026-03-08T06:30:00Z"));
        entry.setEndTime(Instant.parse("2026-03-08T07:30:00Z"));
        entry = entries.saveAndFlush(entry);

        Note note = new Note();
        note.setId(UUID.randomUUID());
        note.setUserId(OWNER);
        note.setTimeEntryId(entry.getId());
        note.setAttachmentOwnerId(OWNER);
        note.setTitle("Private title");
        note.setContentJson(MAPPER.readTree("{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}"));
        note.setContentText("Private preview");
        note.setContentSchemaVersion(1);
        note.setVersion(1);
        notes.saveAndFlush(note);

        var page = service.summaries(OWNER, "2026-03-08", "2026-03-10", "America/New_York");
        assertThat(page.days()).hasSize(2);
        assertThat(page.days().getFirst().workSeconds()).isEqualTo(3600);
        assertThat(page.days().getFirst().sessionCount()).isEqualTo(1);
        assertThat(page.days().getFirst().noteCount()).isEqualTo(1);
        assertThat(page.days().getLast().workSeconds()).isZero();

        var detail = service.detail(OWNER, "2026-03-08", "America/New_York");
        assertThat(detail.generated().sessions()).hasSize(1);
        assertThat(detail.generated().sessions().getFirst().durationSeconds()).isEqualTo(3600);
        assertThat(detail.generated().noteReferences()).singleElement()
                .satisfies(reference -> assertThat(reference.preview()).isEqualTo("Private preview"));

        UUID key = UUID.randomUUID();
        var content = MAPPER.readTree("{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Private reflection\"}]}]}}");
        var created = service.updateReflection(OWNER, "2026-03-08", "America/New_York", key,
                request(content, 0));
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.timeZoneAtCreation()).isEqualTo("America/New_York");
        assertThat(service.updateReflection(OWNER, "2026-03-08", "America/New_York", key,
                request(content, 0))).isEqualTo(created);

        var cleared = service.updateReflection(OWNER, "2026-03-08", "UTC", null,
                request(MAPPER.readTree("{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}"), 1));
        assertThat(cleared.version()).isEqualTo(2);
        assertThat(cleared.timeZoneAtCreation()).isEqualTo("America/New_York");
        assertThat(service.detail(OWNER, "2026-03-08", "UTC").reflection()).isEqualTo(cleared);
        assertThat(service.updateReflection(OWNER, "2026-03-08", "America/New_York", key,
                request(content, 0)).version()).isEqualTo(2);
        assertThat(service.summaries(OWNER, "2026-03-08", "2026-03-09", "UTC").days().getFirst().hasReflectionContent()).isFalse();
        assertThatThrownBy(() -> service.updateReflection(OWNER, "2026-03-08", "UTC", UUID.randomUUID(),
                request(content, 0))).isInstanceOf(ConflictException.class);
    }
}
