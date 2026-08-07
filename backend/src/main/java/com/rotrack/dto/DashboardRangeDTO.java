package com.rotrack.dto;

import java.time.Instant;

public record DashboardRangeDTO(
        Instant start,
        Instant end,
        String timeZone
) {}
