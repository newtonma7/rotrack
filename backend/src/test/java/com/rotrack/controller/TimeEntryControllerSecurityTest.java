package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.model.ActivityType;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.service.TimeEntryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimeEntryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class TimeEntryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithoutTokenReturnsStableUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/time-entries/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap())
                .andExpect(jsonPath("$.path").value("/api/v1/time-entries/active"));
    }

    @Test
    void invalidBearerTokenReturnsStableUnauthorizedEnvelope() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get("/api/v1/time-entries/active")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.message").value("Authentication failed"));
    }

    @Test
    void malformedJsonReturnsStableErrorEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap());
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.activityType").exists());
    }

    @Test
    void idBasedStopReturnsTheServerResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        TimeEntryDTO stopped = new TimeEntryDTO(
                entryId,
                ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                3600L,
                null
        );
        when(timeEntryService.stopSession(userId, entryId)).thenReturn(stopped);

        mockMvc.perform(put("/api/v1/time-entries/{id}/stop", entryId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(entryId.toString()))
                .andExpect(jsonPath("$.data.endTime").value("2026-01-01T11:00:00Z"))
                .andExpect(jsonPath("$.data.durationSeconds").value(3600));
    }

    @Test
    void ownershipMissReturnsNotFoundEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(timeEntryService.stopSession(userId, entryId))
                .thenThrow(new ResourceNotFoundException("Time entry not found"));

        mockMvc.perform(put("/api/v1/time-entries/{id}/stop", entryId)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/time-entries/" + entryId + "/stop"));
    }

    @Test
    void startingSessionReturnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        TimeEntryDTO started = new TimeEntryDTO(
                entryId,
                ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                null
        );
        when(timeEntryService.startSession(userId, ActivityType.WORK, null)).thenReturn(started);

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(entryId.toString()));
    }

    @Test
    void domainConflictReturnsStableConflictEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(timeEntryService.startSession(any(), any(), any()))
                .thenThrow(new ConflictException("ACTIVE_SESSION_EXISTS", "An active session already exists"));

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_SESSION_EXISTS"));
    }

    @Test
    void unexpectedFailureReturnsSanitizedInternalError() throws Exception {
        UUID userId = UUID.randomUUID();
        when(timeEntryService.getActiveSession(userId)).thenThrow(new RuntimeException("database password"));

        mockMvc.perform(get("/api/v1/time-entries/active")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"));
    }
}
