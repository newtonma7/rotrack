package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateNoteRequest(String title, JsonNode contentJson, UUID timeEntryId, long expectedVersion) {}
