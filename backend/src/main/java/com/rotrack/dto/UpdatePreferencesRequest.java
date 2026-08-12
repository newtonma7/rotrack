package com.rotrack.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePreferencesRequest(
        String timezone,
        @Min(value = 1, message = "must be between 1 and 1440")
        @Max(value = 1440, message = "must be between 1 and 1440")
        Integer dailyWorkGoalMinutes,
        boolean shareStudySummary,
        boolean shareActiveStudyStatus
) {
}
