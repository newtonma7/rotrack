package com.rotrack.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.model.ActivityType;
import com.rotrack.service.TimeEntryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimeEntryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer",
        "rotrack.security.rate-limit.requests-per-window=1",
        "rotrack.security.rate-limit.window=1m",
        "rotrack.security.rate-limit.max-keys=10"
})
class MutationRateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedStartEndpointReturnsRateLimitEnvelopeBeforeControllerMutationRepeats() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TimeEntryDTO started = new TimeEntryDTO(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
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
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .header("X-Forwarded-For", "198.51.100.8")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.message").value("Too many mutation requests"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap())
                .andExpect(jsonPath("$.path").value("/api/v1/time-entries/start"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
