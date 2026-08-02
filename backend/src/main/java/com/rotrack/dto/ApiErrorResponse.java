package com.rotrack.dto;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        ErrorDetail error,
        Instant timestamp,
        String path
) {

    public static ApiErrorResponse of(
            String code,
            String message,
            Map<String, String> fieldErrors,
            String path
    ) {
        return new ApiErrorResponse(
                new ErrorDetail(code, message, fieldErrors),
                Instant.now(),
                path
        );
    }

    public record ErrorDetail(
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {}
}
