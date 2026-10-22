package com.rotrack.richtext;

import tools.jackson.databind.JsonNode;

public record RichTextValue(
        JsonNode contentJson,
        String serialized,
        String contentText
) {
    public boolean meaningful() {
        return !contentText.strip().isEmpty();
    }
}
