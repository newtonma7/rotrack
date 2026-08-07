package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Instant;
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
}
