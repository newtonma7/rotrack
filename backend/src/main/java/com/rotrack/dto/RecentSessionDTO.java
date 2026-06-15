package com.rotrack.dto;

import com.rotrack.model.ActivityType;

public record RecentSessionDTO(
        String id,
        String activity,
        String duration,
        ActivityType type,
        String time
) {}
