package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rotrack.dto.HistoryPageDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TimeEntryServiceTest {

    private final TimeEntryRepository repository = mock(TimeEntryRepository.class);
    private final TimeEntryService service = new TimeEntryService(repository);

    @Test
    void stoppingAnAlreadyStoppedSessionReturnsTheOriginalResource() {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Instant originalEnd = Instant.parse("2026-01-01T11:00:00Z");
        TimeEntry entry = new TimeEntry();
        entry.setId(entryId);
        entry.setUserId(userId);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(Instant.parse("2026-01-01T10:00:00Z"));
        entry.setEndTime(originalEnd);
        entry.setDurationMinutes(999);
        entry.setNotes("deep work");

        when(repository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(entry));

        TimeEntryDTO result = service.stopSession(userId, entryId);

        assertEquals(entryId, result.id());
        assertEquals(originalEnd, result.endTime());
        assertEquals(3600L, result.durationSeconds());
        verify(repository, never()).save(any(TimeEntry.class));
    }

    @Test
    void stoppingAnEntryForAnotherUserReturnsNotFoundAndPreservesOwnershipBoundary() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(repository.findByIdAndUserId(entryId, otherUserId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.stopSession(otherUserId, entryId)
        );
        verify(repository).findByIdAndUserId(entryId, otherUserId);
        verify(repository, never()).save(any(TimeEntry.class));
    }

    @Test
    void concurrentStartConflictIsTranslatedToActiveSessionConflict() {
        UUID userId = UUID.randomUUID();
        when(repository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(TimeEntry.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "violates unique constraint idx_time_entries_one_active_per_user"
                ));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.startSession(userId, ActivityType.WORK, null)
        );

        assertEquals("ACTIVE_SESSION_EXISTS", exception.getCode());
    }

    @Test
    void exclusionRaceWithAnActiveRowStillReturnsActiveSessionConflict() {
        UUID userId = UUID.randomUUID();
        when(repository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(TimeEntry.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "time_entries_no_overlap_per_user tstzrange infinity"));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.startSession(userId, ActivityType.WORK, null));

        assertEquals("ACTIVE_SESSION_EXISTS", exception.getCode());
    }

    @Test
    void nonCanonicalHistoryCursorIsRejectedAsUntrustedInput() {
        UUID id = UUID.randomUUID();
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("2026-01-01T10:00:00+00:00|" + id).getBytes(StandardCharsets.UTF_8));

        assertThrows(com.rotrack.exception.InvalidCursorException.class,
                () -> service.listHistory(UUID.randomUUID(), cursor));
    }

    @Test
    void nonCanonicalCursorUuidIsRejected() {
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "2026-01-01T10:00:00Z|ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(com.rotrack.exception.InvalidCursorException.class,
                () -> service.listHistory(UUID.randomUUID(), cursor));
    }

    @Test
    void activeSessionDoesNotExposeTransitionalPersistedDuration() {
        UUID userId = UUID.randomUUID();
        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setStartTime(Instant.parse("2026-01-01T10:00:00Z"));
        entry.setDurationMinutes(999);

        when(repository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId))
                .thenReturn(Optional.of(entry));

        TimeEntryDTO result = service.getActiveSession(userId);

        assertNull(result.durationSeconds());
    }

    @Test
    void historyUsesCompletedOnlyKeysetPagesAndFixedTwentyEntryLimit() {
        UUID userId = UUID.randomUUID();
        TimeEntry newest = entry(userId, Instant.parse("2026-01-02T10:00:00Z"), Instant.parse("2026-01-02T11:00:00Z"));
        TimeEntry twentieth = entry(userId, Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T11:00:00Z"));
        when(repository.findCompletedHistory(eq(userId), any())).thenReturn(java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> index == 20 ? twentieth : newest)
                .toList());

        HistoryPageDTO result = service.listHistory(userId, null);

        assertEquals(20, result.entries().size());
        assertFalse(result.nextCursor().isBlank());
        verify(repository).findCompletedHistory(eq(userId), argThat(pageable -> pageable.getPageSize() == 21));
    }

    @Test
    void invalidHistoryCursorHasStableFailure() {
        assertThrows(com.rotrack.exception.InvalidCursorException.class,
                () -> service.listHistory(UUID.randomUUID(), "not-a-cursor"));
    }

    @Test
    void historyCursorSelectsRowsStrictlyAfterTheLastKey() {
        UUID userId = UUID.randomUUID();
        UUID lastId = UUID.randomUUID();
        Instant lastStart = Instant.parse("2026-01-01T10:00:00Z");
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (lastStart + "|" + lastId).getBytes(StandardCharsets.UTF_8));
        when(repository.findCompletedHistoryAfter(eq(userId), eq(lastStart), eq(lastId), any()))
                .thenReturn(java.util.List.of());

        assertTrue(service.listHistory(userId, cursor).entries().isEmpty());
        verify(repository).findCompletedHistoryAfter(eq(userId), eq(lastStart), eq(lastId),
                argThat(pageable -> pageable.getPageSize() == 21));
    }

    @Test
    void manualOverlapIsRejectedBeforePersistence() {
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        when(repository.existsOverlappingEntry(eq(userId), eq(start), eq(start.plus(Duration.ofHours(1))), isNull()))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createCompletedEntry(
                userId, ActivityType.WORK, start, start.plus(Duration.ofHours(1)), "focus"));
        verify(repository, never()).saveAndFlush(any(TimeEntry.class));
    }

    @Test
    void adjacentManualRangesAreAccepted() {
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T11:00:00Z");
        when(repository.existsOverlappingEntry(eq(userId), eq(start), eq(start.plus(Duration.ofHours(1))), isNull()))
                .thenReturn(false);
        TimeEntry saved = entry(userId, start, start.plus(Duration.ofHours(1)));
        when(repository.saveAndFlush(any(TimeEntry.class))).thenReturn(saved);

        assertEquals(saved.getStartTime(), service.createCompletedEntry(
                userId, ActivityType.WORK, start, start.plus(Duration.ofHours(1)), null).startTime());
    }

    @Test
    void databaseOverlapIsTranslatedToStableConflict() {
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        when(repository.existsOverlappingEntry(eq(userId), eq(start), eq(start.plus(Duration.ofHours(1))), isNull()))
                .thenReturn(false);
        when(repository.saveAndFlush(any(TimeEntry.class)))
                .thenThrow(new DataIntegrityViolationException("time_entries_no_overlap_per_user"));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.createCompletedEntry(
                userId, ActivityType.WORK, start, start.plus(Duration.ofHours(1)), null));
        assertEquals("TIME_ENTRY_OVERLAP", exception.getCode());
    }

    private TimeEntry entry(UUID userId, Instant start, Instant end) {
        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setNotes("note");
        return entry;
    }
}
