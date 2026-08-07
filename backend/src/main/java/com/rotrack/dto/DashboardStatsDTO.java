package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import java.util.List;
import java.util.Map;

public record DashboardStatsDTO(
        DashboardRangeDTO range,
        Map<ActivityType, Long> totalSeconds,
        List<DailyStatsDTO> daily,
        List<TimeEntryDTO> recentSessions,
        int productivityScore
) {}
