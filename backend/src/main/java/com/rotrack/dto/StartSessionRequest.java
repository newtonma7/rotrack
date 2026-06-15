package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartSessionRequest(
        @NotNull ActivityType activityType,
        String notes
) {}
