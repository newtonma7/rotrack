package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.NoteAttachmentFilter;
import com.rotrack.dto.NotePageDTO;
import com.rotrack.dto.NoteRequest;
import com.rotrack.dto.UpdateNoteRequest;
import com.rotrack.dto.NoteDTO;
import com.rotrack.exception.ValidationException;
import com.rotrack.service.NoteService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ApiResponse<NotePageDTO> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String attachment,
            @RequestParam(required = false) UUID timeEntryId
    ) {
        return ApiResponse.success(noteService.list(UUID.fromString(jwt.getSubject()), cursor,
                NoteAttachmentFilter.parse(attachment), timeEntryId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteDTO>> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) NoteRequest request
    ) {
        if (request == null) throw invalidBody();
        UUID key = parseKey(idempotencyKey);
        NoteService.CreateResult result = noteService.create(UUID.fromString(jwt.getSubject()), key, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(result.note()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(noteService.get(UUID.fromString(jwt.getSubject()), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteDTO> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) UpdateNoteRequest request
    ) {
        if (request == null) throw invalidBody();
        return ApiResponse.success(noteService.update(UUID.fromString(jwt.getSubject()), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam String expectedVersion
    ) {
        noteService.delete(UUID.fromString(jwt.getSubject()), id, parseExpectedVersion(expectedVersion));
    }

    private long parseExpectedVersion(String value) {
        try {
            if (value == null || value.isBlank()) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    "expectedVersion must be positive",
                    java.util.Map.of("expectedVersion", "expectedVersion must be positive"));
        }
    }

    private UUID parseKey(String value) {
        try {
            UUID key = UUID.fromString(value);
            if (!key.toString().equals(value)) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException exception) {
            throw new ValidationException(
                    "Idempotency-Key must be a canonical UUID",
                    java.util.Map.of("Idempotency-Key", "Idempotency-Key must be a canonical UUID"));
        }
    }

    private ValidationException invalidBody() {
        return new ValidationException("Request body is required", java.util.Map.of());
    }
}
