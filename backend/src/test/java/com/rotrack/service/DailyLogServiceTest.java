package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.rotrack.config.NoteHmac;
import com.rotrack.dto.ReflectionDTO;
import com.rotrack.dto.UpdateReflectionRequest;
import com.rotrack.exception.ValidationException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.Reflection;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.ReflectionCreationReplayRepository;
import com.rotrack.repository.ReflectionRepository;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.repository.UserPreferencesRepository;
import com.rotrack.richtext.RichTextDocumentValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyLogServiceTest {
    private static final String EMPTY_DOCUMENT = "{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}";

    private final TimeEntryRepository entries = mock(TimeEntryRepository.class);
    private final NoteRepository notes = mock(NoteRepository.class);
    private final ReflectionRepository reflections = mock(ReflectionRepository.class);
    private final ReflectionCreationReplayRepository replays = mock(ReflectionCreationReplayRepository.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final DailyLogService service = new DailyLogService(
            entries,
            notes,
            reflections,
            replays,
            preferences,
            new RichTextDocumentValidator(mapper),
            new NoteHmac("test-only-key")
    );

    @Test
    void summaryClipsCompletedEntriesAcrossDstAndIncludesEmptyDates() {
        TimeEntry entry = entry(
                ActivityType.WORK,
                "2026-03-08T06:30:00Z",
                "2026-03-08T07:30:00Z"
        );
        when(entries.findCompletedOverlappingRange(eq(userId), any(), any())).thenReturn(List.of(entry));

        var result = service.summaries(userId, "2026-03-08", "2026-03-10", "America/New_York");

        assertEquals(2, result.days().size());
        assertEquals(3600, result.days().getFirst().workSeconds());
        assertEquals(1, result.days().getFirst().sessionCount());
        assertEquals(0, result.days().getLast().sessionCount());
        assertEquals("America/New_York", result.range().timeZone());
    }

    @Test
    void rejectsNoncanonicalDatesUnpairedRangesAndRangesLongerThan42Days() {
        assertThrows(ValidationException.class,
                () -> service.summaries(userId, "0000-01-01", "0000-01-02", "UTC"));
        assertThrows(ValidationException.class,
                () -> service.summaries(userId, "2026-01-01", "2026-02-13", "UTC"));
        assertThrows(ValidationException.class, () -> service.summaries(userId, null, null, "UTC"));
        assertThrows(ValidationException.class,
                () -> service.summaries(userId, "2026-01-01", "2026-01-02", ""));
    }

    @Test
    void dateDomainIncludesBothExtremesAndAllowsExactly42Days() {
        when(entries.findCompletedOverlappingRange(eq(userId), any(), any())).thenReturn(List.of());

        var lowerDate = service.detail(userId, "0001-01-01", "UTC");
        var upperDate = service.detail(userId, "9999-12-31", "UTC");
        var summaries = service.summaries(userId, "2026-01-01", "2026-02-12", "UTC");

        assertEquals(LocalDate.of(1, 1, 1), lowerDate.localDate());
        assertEquals(LocalDate.of(9999, 12, 31), upperDate.localDate());
        assertEquals(42, summaries.days().size());
    }

    @Test
    void reflectionCreationRequiresMeaningfulTextAndClearingPreservesIdentityAndIncrementsVersion() throws Exception {
        UUID key = UUID.randomUUID();
        LocalDate date = LocalDate.parse("2026-01-02");
        JsonNode empty = mapper.readTree(EMPTY_DOCUMENT);
        assertThrows(ValidationException.class,
                () -> service.updateReflection(userId, date.toString(), "UTC", key, request(empty, 0)));

        JsonNode meaningful = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Reflection"}]}]}}
                """);
        when(replays.claim(eq(userId), eq(key), any(), any())).thenReturn(1);
        when(reflections.findByUserIdAndLocalDate(userId, date)).thenReturn(Optional.empty());
        when(reflections.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionDTO created = service.updateReflection(userId, date.toString(), "UTC", key, request(meaningful, 0));
        assertEquals(1, created.version());
        assertEquals("UTC", created.timeZoneAtCreation());

        Reflection stored = reflection(date, meaningful, "Reflection", 1, "UTC");
        when(reflections.lockByOwnerAndDate(userId, date)).thenReturn(Optional.of(stored));
        ReflectionDTO cleared = service.updateReflection(
                userId, date.toString(), "America/New_York", null, request(empty, 1));

        assertEquals(2, cleared.version());
        assertEquals("UTC", cleared.timeZoneAtCreation());
        assertEquals("", cleared.contentText());
    }

    private TimeEntry entry(ActivityType type, String start, String end) {
        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setActivityType(type);
        entry.setStartTime(Instant.parse(start));
        entry.setEndTime(Instant.parse(end));
        return entry;
    }

    private Reflection reflection(LocalDate date, JsonNode content, String text, long version, String timeZone) {
        Reflection reflection = new Reflection();
        reflection.setId(UUID.randomUUID());
        reflection.setUserId(userId);
        reflection.setLocalDate(date);
        reflection.setTimeZoneAtCreation(timeZone);
        reflection.setContentJson(content);
        reflection.setContentText(text);
        reflection.setContentSchemaVersion(1);
        reflection.setVersion(version);
        return reflection;
    }

    private UpdateReflectionRequest request(JsonNode content, long expectedVersion) {
        UpdateReflectionRequest request = new UpdateReflectionRequest();
        request.setContentJson(content);
        request.setExpectedVersion(expectedVersion);
        return request;
    }
}
