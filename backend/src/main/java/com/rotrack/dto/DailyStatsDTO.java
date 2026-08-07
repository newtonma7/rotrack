package com.rotrack.dto;

import java.time.LocalDate;

public record DailyStatsDTO(
        LocalDate localDate,
        long workSeconds,
        long rotSeconds
) {}
