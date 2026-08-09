package com.rotrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.model.ActivityType;
import com.rotrack.observability.StructuredRequestLoggingFilter;
import com.rotrack.service.TimeEntryService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(TimeEntryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer",
        "rotrack.security.rate-limit.requests-per-window=1",
        "rotrack.security.rate-limit.window=1m",
        "rotrack.security.rate-limit.max-keys=10"
})
class StructuredRequestLoggingFullChainTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private List<String> events;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        StructuredRequestLoggingFilter loggingFilter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                events::add
        );
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(loggingFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void loggingWrapsSecurityAndCapturesOneUnauthorizedCompletionWithRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/time-entries/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        assertThat(events).hasSize(1);
        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("http.response.status_code").asInt()).isEqualTo(401);
        assertThat(event.get("correlation.id").asText()).hasSize(22);
        assertThat(event.get("error.code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(result.getResponse().getHeader("X-Request-ID"))
                .isEqualTo(event.get("correlation.id").asText());
    }

    @Test
    void loggingCapturesSanitizedUnexpected500Category() throws Exception {
        UUID userId = UUID.randomUUID();
        when(timeEntryService.getActiveSession(userId)).thenThrow(new RuntimeException("private database detail"));

        MvcResult result = mockMvc.perform(get("/api/v1/time-entries/active")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        assertThat(events).hasSize(1);
        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("http.response.status_code").asInt()).isEqualTo(500);
        assertThat(event.get("error.code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(event.get("exception.type").asText()).isEqualTo("unexpected");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private database detail");
        assertThat(events.getFirst()).doesNotContain("private database detail", "RuntimeException");
    }

    @Test
    void loggingWrapsSecurityAndCapturesOneRateLimitedCompletionWithRequestId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(timeEntryService.startSession(userId, ActivityType.WORK, null)).thenReturn(new TimeEntryDTO(
                entryId,
                ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\"}"))
                .andExpect(status().isCreated());
        events.clear();

        MvcResult result = mockMvc.perform(post("/api/v1/time-entries/start")
                        .header("X-Forwarded-For", "198.51.100.99")
                        .header("Forwarded", "for=203.0.113.99")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        assertThat(events).hasSize(1);
        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("http.response.status_code").asInt()).isEqualTo(429);
        assertThat(event.get("correlation.id").asText()).hasSize(22);
        assertThat(event.get("error.code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(result.getResponse().getHeader("X-Request-ID"))
                .isEqualTo(event.get("correlation.id").asText());
    }
}
