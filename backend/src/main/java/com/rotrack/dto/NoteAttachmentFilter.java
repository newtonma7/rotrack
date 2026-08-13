package com.rotrack.dto;

public enum NoteAttachmentFilter {
    ATTACHED, STANDALONE;

    public static NoteAttachmentFilter parse(String value) {
        if (value == null) return null;
        try {
            if (value.isBlank()) throw new IllegalArgumentException();
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new com.rotrack.exception.ValidationException(
                    "attachment must be ATTACHED or STANDALONE",
                    java.util.Map.of("attachment", "attachment must be ATTACHED or STANDALONE"));
        }
    }
}
