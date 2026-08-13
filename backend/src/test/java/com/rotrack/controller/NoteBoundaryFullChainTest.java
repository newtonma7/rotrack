package com.rotrack.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.dto.NoteDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.model.ActivityType;
import com.rotrack.security.NoteRateLimitFilter;
import com.rotrack.security.NoteRateLimiter;
import com.rotrack.service.NoteService;
import com.rotrack.service.TimeEntryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest({NoteController.class, TimeEntryController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class,
        NoteBoundaryFullChainTest.TestRateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer",
        "rotrack.cors.allowed-origins=http://localhost:3101",
        "rotrack.security.rate-limit.requests-per-window=2",
        "rotrack.security.rate-limit.window=1m",
        "rotrack.security.rate-limit.max-keys=10"
})
class NoteBoundaryFullChainTest {
    private static final String ALLOWED_ORIGIN = "http://localhost:3101";
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NOTE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ENTRY_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String NOTE_BODY =
            "{\"title\":\"Title\",\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}},\"timeEntryId\":null}";
    private static final String NOTE_UPDATE_BODY = NOTE_BODY.substring(0, NOTE_BODY.length() - 1)
            + ",\"expectedVersion\":1}";
    private static final String HISTORY_BODY =
            "{\"activityType\":\"WORK\",\"startTime\":\"2026-01-01T10:00:00Z\","
                    + "\"endTime\":\"2026-01-01T11:00:00Z\",\"notes\":null}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock noteClock;

    @MockitoBean
    private NoteService noteService;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void provesCorsAndIndependentNoteAndTimerHistoryBudgetsThroughSecurityChain() throws Exception {
        when(noteService.create(eq(USER), any(), any())).thenReturn(new NoteService.CreateResult(note(), false));
        when(noteService.update(eq(USER), eq(NOTE_ID), any())).thenReturn(note());
        when(timeEntryService.startSession(eq(USER), eq(ActivityType.WORK), isNull())).thenReturn(entry(null));
        when(timeEntryService.createCompletedEntry(
                eq(USER), eq(ActivityType.WORK), any(), any(), isNull())).thenReturn(entry(Instant.parse("2026-01-01T11:00:00Z")));

        mockMvc.perform(options("/api/v1/notes")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")));

        mockMvc.perform(options("/api/v1/notes")
                        .header("Origin", "http://localhost:3100")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Idempotency-Key"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // Two alternating writes in each bucket prove neither Note nor timer/history writes consume the other.
        createNote().andExpect(status().isCreated());
        startTimer().andExpect(status().isCreated());
        updateNote().andExpect(status().isOk());
        createHistoryEntry().andExpect(status().isCreated());

        updateNote()
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        createHistoryEntry().andExpect(status().isTooManyRequests());

        noteClock.advance(Duration.ofMinutes(1));
        updateNote().andExpect(status().isOk());
    }

    private ResultActions createNote() throws Exception {
        return mockMvc.perform(post("/api/v1/notes")
                .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                .header("Origin", ALLOWED_ORIGIN)
                .header("Idempotency-Key", "44444444-4444-4444-8444-444444444444")
                .contentType(MediaType.APPLICATION_JSON)
                .content(NOTE_BODY));
    }

    private ResultActions updateNote() throws Exception {
        return mockMvc.perform(put("/api/v1/notes/{id}", NOTE_ID)
                .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                .header("Origin", ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(NOTE_UPDATE_BODY));
    }

    private ResultActions startTimer() throws Exception {
        return mockMvc.perform(post("/api/v1/time-entries/start")
                .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                .header("Origin", ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activityType\":\"WORK\"}"));
    }

    private ResultActions createHistoryEntry() throws Exception {
        return mockMvc.perform(post("/api/v1/time-entries")
                .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                .header("Origin", ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(HISTORY_BODY));
    }

    private NoteDTO note() {
        return new NoteDTO(NOTE_ID, "Title", "", null, 1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), "", 1);
    }

    private TimeEntryDTO entry(Instant endTime) {
        return new TimeEntryDTO(ENTRY_ID, ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"), endTime,
                endTime == null ? null : 3600L, null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRateLimitConfiguration {
        @Bean
        MutableClock noteClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }

        @Bean
        NoteRateLimiter noteRateLimiter(MutableClock clock) {
            return new NoteRateLimiter(2, Duration.ofMinutes(1), 10, clock);
        }

        @Bean
        NoteRateLimitFilter noteRateLimitFilter(NoteRateLimiter limiter, ObjectMapper objectMapper) {
            return new NoteRateLimitFilter(limiter, objectMapper);
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
