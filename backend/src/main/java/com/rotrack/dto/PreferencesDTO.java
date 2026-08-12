package com.rotrack.dto;

public record PreferencesDTO(
        String timezone,
        Integer dailyWorkGoalMinutes,
        boolean shareStudySummary,
        boolean shareActiveStudyStatus
) {
}
