package com.rotrack.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReflectionDTO(
        JsonNode contentJson,
        String contentText,
        int contentSchemaVersion,
        long version,
        String timeZoneAtCreation,
        Instant updatedAt
) {
}
