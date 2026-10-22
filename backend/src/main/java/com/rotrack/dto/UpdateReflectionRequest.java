package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class UpdateReflectionRequest {
    private JsonNode contentJson;
    private Long expectedVersion;
    private boolean containsUnknownProperties;

    public JsonNode contentJson() {
        return contentJson;
    }

    public void setContentJson(JsonNode contentJson) {
        this.contentJson = contentJson;
    }

    public Long expectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, JsonNode value) {
        containsUnknownProperties = true;
    }

    @JsonIgnore
    public boolean containsUnknownProperties() {
        return containsUnknownProperties;
    }
}
