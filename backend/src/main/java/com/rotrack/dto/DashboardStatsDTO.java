package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import java.util.List;
import java.util.Map;

public record DashboardStatsDTO(
        Map<ActivityType, Integer> totalMinutes,
        List<TimelinePointDTO> timeline,
        List<RecentSessionDTO> recentSessions,
        int productivityScore
) {}
