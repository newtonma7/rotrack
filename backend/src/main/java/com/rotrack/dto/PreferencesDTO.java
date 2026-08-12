package com.rotrack.dto;

public record PreferencesDTO(
        String timeZone,
        Integer dailyWorkGoalMinutes,
        boolean shareStudySummary,
        boolean shareActiveStudyStatus
) {
}
