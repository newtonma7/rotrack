package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import java.time.Instant;
import java.util.UUID;

public record TimeEntryDTO(
        UUID id,
        ActivityType activityType,
        Instant startTime,
        Instant endTime,
        Long durationSeconds,
        String notes
) {}
