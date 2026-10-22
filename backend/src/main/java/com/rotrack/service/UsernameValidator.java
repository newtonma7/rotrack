package com.rotrack.service;

import com.rotrack.exception.ValidationException;
import java.util.Locale;
import java.util.Map;

public final class UsernameValidator {
    private UsernameValidator() {}

    public static String canonicalize(String value) {
        if (value == null) {
            throw invalid("A canonical username of 3–24 characters is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,24}")) {
            throw invalid("Use 3–24 letters, numbers, or underscores");
        }
        return normalized;
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(message, Map.of("username", message));
    }
}
