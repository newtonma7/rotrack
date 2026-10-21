package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.DailyLogDTO;
import com.rotrack.dto.ReflectionDTO;
import com.rotrack.dto.UpdateReflectionRequest;
import com.rotrack.exception.ValidationException;
import com.rotrack.service.DailyLogService;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-logs")
public class DailyLogController {
    private final DailyLogService service;

    public DailyLogController(DailyLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DailyLogDTO.SummaryPage> summaries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String timeZone
    ) {
        return ApiResponse.success(service.summaries(UUID.fromString(jwt.getSubject()), start, end, timeZone));
    }

    @GetMapping("/{localDate}")
    public ApiResponse<DailyLogDTO.Detail> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String localDate,
            @RequestParam(required = false) String timeZone
    ) {
        return ApiResponse.success(service.detail(UUID.fromString(jwt.getSubject()), localDate, timeZone));
    }

    @PutMapping("/{localDate}/reflection")
    public ApiResponse<ReflectionDTO> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String localDate,
            @RequestParam(required = false) String timeZone,
            @RequestHeader(value = "Idempotency-Key", required = false) String rawKey,
            @RequestBody(required = false) UpdateReflectionRequest request
    ) {
        if (request == null) {
            throw new ValidationException("Request body is required", Map.of("body", "Request body is required"));
        }
        if (request.expectedVersion() == null) {
            throw new ValidationException("expectedVersion is required", Map.of("expectedVersion", "expectedVersion is required"));
        }
        if (request.containsUnknownProperties()) {
            throw new ValidationException("Request contains unknown fields", Map.of("body", "Request contains unknown fields"));
        }
        UUID key = parseKey(rawKey);
        return ApiResponse.success(service.updateReflection(
                UUID.fromString(jwt.getSubject()), localDate, timeZone, key, request));
    }

    private UUID parseKey(String value) {
        if (value == null) {
            return null;
        }
        try {
            UUID key = UUID.fromString(value);
            if (!key.toString().equals(value)) {
                throw new IllegalArgumentException("noncanonical key");
            }
            return key;
        } catch (RuntimeException exception) {
            throw new ValidationException("Idempotency-Key must be a canonical UUID",
                    Map.of("Idempotency-Key", "Idempotency-Key must be a canonical UUID"));
        }
    }
}
