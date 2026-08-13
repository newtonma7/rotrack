package com.rotrack.richtext;

import com.fasterxml.jackson.databind.JsonNode;

public record RichTextValue(
        JsonNode contentJson,
        String serialized,
        String contentText
) {
    public boolean meaningful() {
        return !contentText.strip().isEmpty();
    }
}
