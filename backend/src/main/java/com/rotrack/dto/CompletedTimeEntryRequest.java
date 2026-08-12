package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rotrack.model.ActivityType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CompletedTimeEntryRequest(
        @NotNull ActivityType activityType,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Size(max = 280) String notes
) {
    @JsonIgnore
    @AssertTrue(message = "endTime must be after startTime")
    public boolean isValidRange() {
        return startTime != null && endTime != null && endTime.isAfter(startTime);
    }
}
