package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rotrack.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record StartSessionRequest(
        @NotNull ActivityType activityType,
        @Size(max = 280) String notes
) {}
