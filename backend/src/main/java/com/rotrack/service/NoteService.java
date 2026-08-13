package com.rotrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotrack.config.NoteHmac;
import com.rotrack.dto.NoteAttachmentFilter;
import com.rotrack.dto.NoteDTO;
import com.rotrack.dto.NotePageDTO;
import com.rotrack.dto.NoteRequest;
import com.rotrack.dto.NoteSummaryDTO;
import com.rotrack.dto.UpdateNoteRequest;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.NoteDeletedException;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.exception.ValidationException;
import com.rotrack.model.Note;
import com.rotrack.model.NoteCreationReplay;
import com.rotrack.repository.NoteCreationReplayRepository;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.richtext.RichTextDocumentValidator;
import com.rotrack.richtext.RichTextValidationException;
import com.rotrack.richtext.RichTextValue;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private static final int PAGE_SIZE = 20;

    private final NoteRepository noteRepository;
    private final NoteCreationReplayRepository replayRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final RichTextDocumentValidator validator;
    private final NoteHmac hmac;

    @Autowired
    public NoteService(
            NoteRepository noteRepository,
            NoteCreationReplayRepository replayRepository,
            TimeEntryRepository timeEntryRepository,
            RichTextDocumentValidator validator,
            NoteHmac hmac
    ) {
        this.noteRepository = noteRepository;
        this.replayRepository = replayRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.validator = validator;
        this.hmac = hmac;
    }

    public NotePageDTO list(UUID userId, String cursor, NoteAttachmentFilter attachment, UUID timeEntryId) {
        Cursor decoded = decodeCursor(cursor);
        Boolean attached = attachment == null ? null : attachment == NoteAttachmentFilter.ATTACHED;
        PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);
        List<Note> rows = decoded == null
                ? noteRepository.findSummaries(userId, attached, timeEntryId, page)
                : noteRepository.findSummariesAfter(userId, attached, timeEntryId,
                        decoded.updatedAt(), decoded.id(), page);
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<Note> pageRows = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        String next = hasNext ? encodeCursor(pageRows.getLast()) : null;
        return new NotePageDTO(pageRows.stream().map(this::toSummary).toList(), next);
    }

    public NoteDTO get(UUID userId, UUID noteId) {
        return toDto(noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found")));
    }

    @Transactional
    public CreateResult create(UUID userId, UUID idempotencyKey, NoteRequest request) {
        hmac.requireWritesEnabled();
        Prepared prepared = prepare(request.title(), request.contentJson(), request.timeEntryId(), true);
        byte[] fingerprint = hmac.fingerprint(prepared.title(), prepared.value().serialized(),
                prepared.timeEntryId() == null ? null : prepared.timeEntryId().toString());

        var existing = replayRepository.findByOwnerIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            NoteCreationReplay replay = existing.get();
            if (!hmac.matches(fingerprint, replay.getFingerprint())) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "The creation key was already used");
            }
            if (replay.getDeletedVersion() != null) {
                throw new NoteDeletedException();
            }
            return new CreateResult(get(userId, replay.getNoteId()), true);
        }

        if (prepared.timeEntryId() != null) {
            ensureAttachment(userId, prepared.timeEntryId());
        }
        Note note = new Note();
        note.setId(UUID.randomUUID());

        // Claim before inserting the Note. A losing transaction must not create a provisional row
        // that a later conflict/NOTE_DELETED rollback could leave behind.
        int claimed = replayRepository.claim(userId, idempotencyKey, fingerprint, note.getId());
        if (claimed == 0) {
            NoteCreationReplay winner = replayRepository.findByOwnerIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
            if (!hmac.matches(fingerprint, winner.getFingerprint())) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "The creation key was already used");
            }
            if (winner.getDeletedVersion() != null) throw new NoteDeletedException();
            return new CreateResult(get(userId, winner.getNoteId()), true);
        }

        apply(note, userId, prepared, 1);
        noteRepository.saveAndFlush(note);
        return new CreateResult(toDto(note), false);
    }

    @Transactional
    public NoteDTO update(UUID userId, UUID noteId, UpdateNoteRequest request) {
        hmac.requireWritesEnabled();
        if (request.expectedVersion() <= 0) {
            throw validation("expectedVersion must be positive", Map.of("expectedVersion", "expectedVersion must be positive"));
        }
        Prepared prepared = prepare(request.title(), request.contentJson(), request.timeEntryId(), false);
        Note visible = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        if (visible.getVersion() != request.expectedVersion()) {
            throw versionConflict();
        }
        // Attachment updates lock Time Entry first; TimeEntryService.deleteEntry does the same
        // before PostgreSQL's ON DELETE SET NULL can lock the attached Note row.
        if (prepared.timeEntryId() != null) {
            ensureAttachment(userId, prepared.timeEntryId());
        }
        Note note = noteRepository.findForUpdateByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        if (note.getVersion() != request.expectedVersion()) {
            throw versionConflict();
        }
        apply(note, userId, prepared, note.getVersion() + 1);
        return toDto(noteRepository.saveAndFlush(note));
    }

    @Transactional
    public void delete(UUID userId, UUID noteId, long expectedVersion) {
        hmac.requireWritesEnabled();
        if (expectedVersion <= 0) {
            throw validation("expectedVersion must be positive", Map.of("expectedVersion", "expectedVersion must be positive"));
        }
        var note = noteRepository.findForUpdateByIdAndUserId(noteId, userId);
        if (note.isEmpty()) {
            var replay = replayRepository.findByOwnerIdAndNoteId(userId, noteId);
            if (replay.isPresent() && replay.get().getDeletedVersion() != null) {
                if (replay.get().getDeletedVersion() == expectedVersion) return;
                throw versionConflict();
            }
            throw new ResourceNotFoundException("Note not found");
        }
        Note current = note.get();
        if (current.getVersion() != expectedVersion) {
            throw versionConflict();
        }
        NoteCreationReplay replay = replayRepository.findByOwnerIdAndNoteId(userId, noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        replay.setDeletedVersion(expectedVersion);
        replayRepository.saveAndFlush(replay);
        noteRepository.delete(current);
        noteRepository.flush();
    }

    private Prepared prepare(String rawTitle, JsonNode document, UUID timeEntryId, boolean requireMeaningful) {
        String title = rawTitle == null ? null : rawTitle.strip();
        if (title != null && title.isBlank()) title = null;
        if (title != null && title.codePointCount(0, title.length()) > 120) {
            throw validation("title exceeds the maximum length", Map.of("title", "title must be 120 Unicode code points or fewer"));
        }
        RichTextValue value;
        try {
            value = validator.validate(document);
        } catch (RichTextValidationException exception) {
            throw validation(exception.getMessage(), Map.of("contentJson", exception.getMessage()));
        }
        if (requireMeaningful && (title == null || title.isBlank()) && !value.meaningful()) {
            String message = "title or contentJson must contain meaningful content";
            throw validation(message, Map.of("title", message, "contentJson", message));
        }
        return new Prepared(title, value, timeEntryId);
    }

    private void ensureAttachment(UUID userId, UUID timeEntryId) {
        timeEntryRepository.findByIdAndUserId(timeEntryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));
    }

    private void apply(Note note, UUID userId, Prepared prepared, long version) {
        note.setUserId(userId);
        note.setTimeEntryId(prepared.timeEntryId());
        note.setAttachmentOwnerId(prepared.timeEntryId() == null ? null : userId);
        note.setTitle(prepared.title());
        note.setContentJson(prepared.value().contentJson());
        note.setContentText(prepared.value().contentText());
        note.setContentSchemaVersion(1);
        note.setVersion(version);
    }

    private NoteDTO toDto(Note note) {
        return new NoteDTO(note.getId(), note.getTitle(),
                preview(note.getContentText()), note.getTimeEntryId(),
                note.getVersion(), note.getCreatedAt(), note.getUpdatedAt(), note.getContentJson(),
                note.getContentText(), note.getContentSchemaVersion());
    }

    private NoteSummaryDTO toSummary(Note note) {
        return new NoteSummaryDTO(note.getId(), note.getTitle(),
                preview(note.getContentText()), note.getTimeEntryId(),
                note.getVersion(), note.getCreatedAt(), note.getUpdatedAt());
    }

    private String preview(String contentText) {
        String collapsed = contentText.replaceAll("[\\p{javaWhitespace}\\p{Z}]+", " ").strip();
        int end = collapsed.offsetByCodePoints(0, Math.min(160, collapsed.codePointCount(0, collapsed.length())));
        return collapsed.substring(0, end);
    }

    private Cursor decodeCursor(String value) {
        if (value == null) return null;
        try {
            if (value.isBlank()) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            String payload = new String(bytes, StandardCharsets.UTF_8);
            if (!value.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) throw new IllegalArgumentException();
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            Instant updated = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            if (!updated.toString().equals(parts[0]) || !id.toString().equals(parts[1])) throw new IllegalArgumentException();
            return new Cursor(updated, id);
        } catch (RuntimeException exception) {
            throw new InvalidCursorException();
        }
    }

    private String encodeCursor(Note note) {
        String payload = note.getUpdatedAt() + "|" + note.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private ValidationException validation(String message, Map<String, String> fields) {
        return new ValidationException(message, fields);
    }

    private ConflictException versionConflict() {
        return new ConflictException("RICH_TEXT_VERSION_CONFLICT", "The Note has changed; reload or preserve your local edits");
    }

    public record CreateResult(NoteDTO note, boolean replayed) {}
    private record Prepared(String title, RichTextValue value, UUID timeEntryId) {}
    private record Cursor(Instant updatedAt, UUID id) {}
}
