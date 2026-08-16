package com.rotrack.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.NoteDTO;
import com.rotrack.dto.NotePageDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.service.NoteService;
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

@WebMvcTest(NoteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class NoteControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID KEY = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean NoteService noteService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void initialCreateIs201AndIdenticalReplayIs200() throws Exception {
        NoteDTO note = note();
        when(noteService.create(any(), any(), any()))
                .thenReturn(new NoteService.CreateResult(note, false))
                .thenReturn(new NoteService.CreateResult(note, true));
        String body = "{\"title\":\"Title\",\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}},\"timeEntryId\":null}";

        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .header("Idempotency-Key", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(note.id().toString()));
        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .header("Idempotency-Key", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void taskDocumentUsesCreateReadUpdateApiSeamsWithoutChangingTheEnvelope() throws Exception {
        NoteDTO note = new NoteDTO(UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "Task", "before", null, 1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                taskDocument(false, "before"), "before", 1);
        when(noteService.create(any(), any(), any())).thenReturn(new NoteService.CreateResult(note, false));
        when(noteService.get(USER, note.id())).thenReturn(note);
        when(noteService.update(eq(USER), eq(note.id()), any())).thenReturn(note);
        String body = taskNoteBody();
        String updateBody = body.substring(0, body.length() - 1) + ",\"expectedVersion\":1}";

        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .header("Idempotency-Key", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentJson.document.content[0].content[0].attrs.checked").value(false));
        mockMvc.perform(get("/api/v1/notes/{id}", note.id())
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentText").value("before"));
        mockMvc.perform(put("/api/v1/notes/{id}", note.id())
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());
    }

    @Test
    void publicGetListUpdateAndDeleteUseAuthenticatedOwnerAndStableErrors() throws Exception {
        NoteDTO note = note();
        when(noteService.list(USER, null, null, null)).thenReturn(new NotePageDTO(List.of(
                new com.rotrack.dto.NoteSummaryDTO(note.id(), note.title(), note.preview(), note.timeEntryId(),
                        note.version(), note.createdAt(), note.updatedAt())), null));
        when(noteService.get(USER, note.id())).thenReturn(note);
        when(noteService.update(eq(USER), eq(note.id()), any())).thenReturn(note);
        doThrow(new ConflictException("RICH_TEXT_VERSION_CONFLICT", "The Note has changed"))
                .when(noteService).delete(USER, note.id(), 1);

        mockMvc.perform(get("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes[0].id").value(note.id().toString()));
        mockMvc.perform(get("/api/v1/notes/{id}", note.id())
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentSchemaVersion").value(1));
        mockMvc.perform(put("/api/v1/notes/{id}", note.id())
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}},\"timeEntryId\":null,\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/notes/{id}", note.id()).queryParam("expectedVersion", "1")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RICH_TEXT_VERSION_CONFLICT"));

        when(noteService.get(USER, note.id())).thenThrow(new ResourceNotFoundException("Note not found"));
        mockMvc.perform(get("/api/v1/notes/{id}", note.id())
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCursorUsesStableApiError() throws Exception {
        when(noteService.list(USER, "bad", null, null)).thenThrow(new InvalidCursorException());

        mockMvc.perform(get("/api/v1/notes")
                        .queryParam("cursor", "bad")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    @Test
    void invalidKeyAndMissingKeyUseSafeValidationErrors() throws Exception {
        String body = "{\"title\":\"Title\",\"contentJson\":{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}}";
        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Idempotency-Key must be a canonical UUID"));
        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void omittedExpectedVersionIsValidationErrorWithFieldError() throws Exception {
        mockMvc.perform(delete("/api/v1/notes/{id}", KEY)
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.expectedVersion").value("expectedVersion is required"));
    }

    @Test
    void nonnumericExpectedVersionIsValidationError() throws Exception {
        mockMvc.perform(delete("/api/v1/notes/{id}", KEY).queryParam("expectedVersion", "not-a-number")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.expectedVersion").value("expectedVersion must be positive"));
    }

    @Test
    void jsonNullCreateAndUpdateBodiesAreValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .header("Idempotency-Key", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/notes/{id}", KEY)
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void blankAttachmentAndExpectedVersionAreValidationErrors() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/notes")
                        .queryParam("attachment", "" )
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(delete("/api/v1/notes/{id}", KEY)
                        .queryParam("expectedVersion", "")
                        .with(jwt().jwt(token -> token.subject(USER.toString()).audience(List.of("authenticated")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private String taskNoteBody() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = (com.fasterxml.jackson.databind.node.ObjectNode) taskDocument(false, "before");
        body.put("title", "Task");
        body.putNull("timeEntryId");
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
    }

    private com.fasterxml.jackson.databind.JsonNode taskDocument(boolean checked, String text) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":%s},"content":[
                      {"type":"paragraph","content":[{"type":"text","text":"%s"}]}
                    ]}
                  ]}
                ]}}
                """.formatted(checked, text));
    }

    private NoteDTO note() {
        return new NoteDTO(UUID.fromString("33333333-3333-4333-8333-333333333333"), "Title", "", null,
                1, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), "", 1);
    }
}
