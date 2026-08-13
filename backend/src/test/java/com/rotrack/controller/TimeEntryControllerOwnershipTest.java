package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.config.TimeConfig;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.service.DashboardService;
import com.rotrack.service.TimeEntryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP ownership coverage with the real services. The repository remains a test
 * boundary, but its answers are owner-dependent so removing a user-id scope from
 * a service query makes these requests fail.
 */
@WebMvcTest({TimeEntryController.class, DashboardController.class})
@Import({
        SecurityConfig.class,
        TimeConfig.class,
        GlobalExceptionHandler.class,
        TimeEntryService.class,
        DashboardService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class TimeEntryControllerOwnershipTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ACTIVE_ENTRY_ID = UUID.fromString("aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa");
    private static final UUID COMPLETED_ENTRY_ID = UUID.fromString("aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryRepository timeEntryRepository;

    @MockitoBean
    private NoteRepository noteRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private TimeEntry activeEntry;
    private TimeEntry completedEntry;

    @BeforeEach
    void setUpOwnerScopedRepository() {
        activeEntry = entry(ACTIVE_ENTRY_ID, USER_A, Instant.parse("2026-01-01T10:00:00Z"), null);
        completedEntry = entry(
                COMPLETED_ENTRY_ID,
                USER_A,
                Instant.parse("2026-01-01T08:00:00Z"),
                Instant.parse("2026-01-01T09:00:00Z")
        );

        when(timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(any()))
                .thenAnswer(invocation -> USER_A.equals(invocation.getArgument(0))
                        ? Optional.of(activeEntry)
                        : Optional.empty());
        when(timeEntryRepository.findByIdAndUserId(any(), any()))
                .thenAnswer(invocation -> ACTIVE_ENTRY_ID.equals(invocation.getArgument(0))
                        && USER_A.equals(invocation.getArgument(1))
                        ? Optional.of(activeEntry)
                        : Optional.empty());
        when(timeEntryRepository.findCompletedOverlappingRange(any(), any(), any()))
                .thenAnswer(invocation -> USER_A.equals(invocation.getArgument(0))
                        ? List.of(completedEntry)
                        : List.of());
        when(timeEntryRepository.save(any(TimeEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void userBCannotReadUserAsEntryThroughExistingReadPaths() throws Exception {
        mockMvc.perform(get("/api/v1/time-entries/active").with(user(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "UTC")
                        .queryParam("start", "2026-01-01")
                        .queryParam("end", "2026-01-02")
                        .with(user(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSeconds.WORK").value(0))
                .andExpect(jsonPath("$.data.recentSessions").isEmpty());

        mockMvc.perform(get("/api/v1/time-entries/active").with(user(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ACTIVE_ENTRY_ID.toString()));

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .queryParam("timeZone", "UTC")
                        .queryParam("start", "2026-01-01")
                        .queryParam("end", "2026-01-02")
                        .with(user(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSeconds.WORK").value(3600))
                .andExpect(jsonPath("$.data.recentSessions[0].id").value(COMPLETED_ENTRY_ID.toString()));

        verify(timeEntryRepository).findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(USER_B);
        verify(timeEntryRepository).findCompletedOverlappingRange(
                USER_B,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    @Test
    void userBReceivesNotFoundWhenStoppingUserAsEntry() throws Exception {
        mockMvc.perform(put("/api/v1/time-entries/{id}/stop", ACTIVE_ENTRY_ID).with(user(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Time entry not found"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/v1/time-entries/{id}/stop"));

        verify(timeEntryRepository).findByIdAndUserId(ACTIVE_ENTRY_ID, USER_B);
        verify(timeEntryRepository, never()).save(any(TimeEntry.class));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).audience(List.of("authenticated")));
    }

    private static TimeEntry entry(UUID id, UUID userId, Instant start, Instant end) {
        TimeEntry entry = new TimeEntry();
        entry.setId(id);
        entry.setUserId(userId);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setCreatedAt(start);
        entry.setUpdatedAt(end == null ? start : end);
        return entry;
    }
}
