package com.rotrack.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteSummaryDTO(
        UUID id,
        String title,
        String preview,
        UUID timeEntryId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
