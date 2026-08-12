package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.HistoryPageDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
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
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class TimeEntryHistoryControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENTRY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listsOwnedCompletedEntriesWithOpaqueCursor() throws Exception {
        TimeEntryDTO entry = completed();
        when(timeEntryService.listHistory(USER_ID, "opaque-next"))
                .thenReturn(new HistoryPageDTO(List.of(entry), "opaque-next"));

        mockMvc.perform(get("/api/v1/time-entries/history")
                        .queryParam("cursor", "opaque-next")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].id").value(ENTRY_ID.toString()))
                .andExpect(jsonPath("$.data.entries[0].durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.nextCursor").value("opaque-next"));

        verify(timeEntryService).listHistory(USER_ID, "opaque-next");
    }

    @Test
    void createsCompletedEntryWithoutClientOwnedFields() throws Exception {
        when(timeEntryService.createCompletedEntry(
                eq(USER_ID), eq(ActivityType.WORK), eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(Instant.parse("2026-01-01T11:00:00Z")), eq("focus")))
                .thenReturn(completed());

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activityType":"WORK","startTime":"2026-01-01T10:00:00Z",
                                 "endTime":"2026-01-01T11:00:00Z","notes":"focus",
                                 "userId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","durationSeconds":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durationSeconds").value(3600));
    }

    @Test
    void rejectsInvalidCursorWithStableError() throws Exception {
        when(timeEntryService.listHistory(USER_ID, "bad"))
                .thenThrow(new InvalidCursorException());

        mockMvc.perform(get("/api/v1/time-entries/history")
                        .queryParam("cursor", "bad")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    @Test
    void validatesRangeAndNotesAtTheHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/time-entries")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"+
                                "\"activityType\":\"WORK\",\"startTime\":\"2026-01-01T11:00:00Z\","+
                                "\"endTime\":\"2026-01-01T10:00:00Z\",\"notes\":\"focus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.endTime").value("endTime must be after startTime"));
    }

    @Test
    void ownershipViolationsUseNotFoundForEditAndDelete() throws Exception {
        doThrow(new ResourceNotFoundException("Time entry not found"))
                .when(timeEntryService).deleteEntry(USER_ID, ENTRY_ID);
        when(timeEntryService.updateCompletedEntry(eq(USER_ID), eq(ENTRY_ID), any()))
                .thenThrow(new ResourceNotFoundException("Time entry not found"));
        String body = "{\"activityType\":\"WORK\",\"startTime\":\"2026-01-01T10:00:00Z\","
                + "\"endTime\":\"2026-01-01T11:00:00Z\"}";

        mockMvc.perform(put("/api/v1/time-entries/{id}", ENTRY_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/time-entries/{id}", ENTRY_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void editsAndDeletesOnlyThroughOwnedIdBoundary() throws Exception {
        when(timeEntryService.updateCompletedEntry(eq(USER_ID), eq(ENTRY_ID), any()))
                .thenReturn(completed());

        mockMvc.perform(put("/api/v1/time-entries/{id}", ENTRY_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"+
                                "\"activityType\":\"ROT\",\"startTime\":\"2026-01-01T10:00:00Z\","+
                                "\"endTime\":\"2026-01-01T11:00:00Z\",\"notes\":null}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/time-entries/{id}", ENTRY_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isNoContent());

        verify(timeEntryService).deleteEntry(USER_ID, ENTRY_ID);
    }

    private TimeEntryDTO completed() {
        return new TimeEntryDTO(
                ENTRY_ID,
                ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                3600L,
                "focus"
        );
    }
}
