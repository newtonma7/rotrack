package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import java.time.Instant;
import java.util.UUID;

/** History-only projection; attached note counts do not belong on active/session DTOs. */
public record HistoryTimeEntryDTO(
        UUID id,
        ActivityType activityType,
        Instant startTime,
        Instant endTime,
        Long durationSeconds,
        String notes,
        long attachedNoteCount
) {}
