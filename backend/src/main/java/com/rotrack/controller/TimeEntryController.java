package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.CompletedTimeEntryRequest;
import com.rotrack.dto.HistoryPageDTO;
import com.rotrack.dto.StartSessionRequest;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.service.TimeEntryService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;

    public TimeEntryController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/time-entries/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimeEntryDTO> startSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartSessionRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.startSession(userId, request.activityType(), request.notes()));
    }

    @GetMapping("/time-entries")
    public ApiResponse<HistoryPageDTO> listHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.listHistory(userId, cursor));
    }

    @PostMapping("/time-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimeEntryDTO> createCompletedEntry(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CompletedTimeEntryRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.createCompletedEntry(
                userId,
                request.activityType(),
                request.startTime(),
                request.endTime(),
                request.notes()
        ));
    }

    @PutMapping("/time-entries/{id}")
    public ApiResponse<TimeEntryDTO> updateCompletedEntry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CompletedTimeEntryRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.updateCompletedEntry(userId, id, request));
    }

    @DeleteMapping("/time-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        timeEntryService.deleteEntry(UUID.fromString(jwt.getSubject()), id);
    }

    @PutMapping("/time-entries/{id}/stop")
    public ApiResponse<TimeEntryDTO> stopSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.stopSession(userId, id));
    }

    @GetMapping("/time-entries/active")
    public ApiResponse<TimeEntryDTO> getActiveSession(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(timeEntryService.getActiveSession(userId));
    }
}
