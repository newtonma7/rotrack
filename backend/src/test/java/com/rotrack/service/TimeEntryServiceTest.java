package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeEntryServiceTest {

    private final TimeEntryRepository repository = mock(TimeEntryRepository.class);
    private final TimeEntryService service = new TimeEntryService(repository);

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

        assertNull(result.durationMinutes());
    }
}
