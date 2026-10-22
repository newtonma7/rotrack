package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record NoteRequest(String title, JsonNode contentJson, UUID timeEntryId) {}
