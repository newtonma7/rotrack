package com.rotrack.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

public final class FriendRequest {
    private String username;
    private boolean unknownFields;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    @JsonAnySetter
    public void rejectUnknown(String name, JsonNode value) { unknownFields = true; }
    @JsonIgnore
    public boolean hasUnknownFields() { return unknownFields; }
}
