package com.rotrack.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record NoteDTO(
        UUID id,
        String title,
        String preview,
        UUID timeEntryId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        JsonNode contentJson,
        String contentText,
        int contentSchemaVersion
) {}
