package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.NoteHmac;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.NoteDeletedException;
import com.rotrack.model.Note;
import com.rotrack.model.NoteCreationReplay;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.NoteCreationReplayRepository;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.richtext.RichTextDocumentValidator;
import com.rotrack.dto.NoteRequest;
import com.rotrack.dto.UpdateNoteRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class NoteServiceTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID KEY = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final NoteRepository notes = org.mockito.Mockito.mock(NoteRepository.class);
    private final NoteCreationReplayRepository replays = org.mockito.Mockito.mock(NoteCreationReplayRepository.class);
    private final TimeEntryRepository entries = org.mockito.Mockito.mock(TimeEntryRepository.class);
    private final NoteService service = new NoteService(
            notes, replays, entries,
            new RichTextDocumentValidator(new ObjectMapper()),
            new NoteHmac("test-secret-test-secret-test-secret-test-secret"));

    @BeforeEach
    void emptyReplay() {
        when(replays.findByOwnerIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(replays.claim(any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void claimsReplayBeforePersistingNoteToAvoidRollbackOrphans() throws Exception {
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(USER, KEY, request("Title", ""));

        InOrder order = org.mockito.Mockito.inOrder(replays, notes);
        order.verify(replays).claim(any(), any(), any(), any());
        order.verify(notes).saveAndFlush(any(Note.class));
    }

    @Test
    void identicalCreateReplayReturnsSameNoteWithoutMutation() throws Exception {
        NoteRequest request = request("Title", "");
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(replays.saveAndFlush(any(NoteCreationReplay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.create(USER, KEY, request);
        Note saved = captureNote();
        when(replays.findByOwnerIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(replay(saved, null)));
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));

        var second = service.create(USER, KEY, request);

        assertThat(first.note().id()).isEqualTo(second.note().id());
        assertThat(second.replayed()).isTrue();
        verify(notes).saveAndFlush(any(Note.class));
        verify(replays).claim(any(), any(), any(), any());
    }

    @Test
    void losingFirstCreateClaimReturnsCommittedWinnerInsteadOfCreatingAnotherNote() throws Exception {
        Note winner = note();
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(replays.claim(any(), any(), any(), any())).thenReturn(0);
        when(replays.findByOwnerIdAndIdempotencyKey(USER, KEY))
                .thenReturn(Optional.empty(), Optional.of(replay(winner, null)));
        when(notes.findByIdAndUserId(winner.getId(), USER)).thenReturn(Optional.of(winner));

        var result = service.create(USER, KEY, request("Title", ""));

        assertThat(result.replayed()).isTrue();
        assertThat(result.note().id()).isEqualTo(winner.getId());
        verify(notes, never()).saveAndFlush(any(Note.class));
    }

    @Test
    void untitledMeaningfulCreateAndReplayUseStableNullableHmacEncoding() throws Exception {
        NoteRequest request = request(null, "meaningful text");
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.create(USER, KEY, request);
        Note saved = captureNote();
        String canonical = "{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"meaningful text\"}]}]}}";
        NoteCreationReplay replay = replay(saved, null);
        replay.setFingerprint(new NoteHmac("test-secret-test-secret-test-secret-test-secret")
                .fingerprint(null, canonical, null));
        when(replays.findByOwnerIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(replay));
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));

        var second = service.create(USER, KEY, request);

        assertThat(first.note().title()).isNull();
        assertThat(first.note().contentText()).isEqualTo("meaningful text");
        assertThat(second.replayed()).isTrue();
        assertThat(second.note().id()).isEqualTo(first.note().id());
    }

    @Test
    void emptyCreateIsRejectedWithCrossFieldValidation() throws Exception {
        assertThatThrownBy(() -> service.create(USER, KEY, request("", "")))
                .isInstanceOf(com.rotrack.exception.ValidationException.class)
                .hasMessage("title or contentJson must contain meaningful content");
        verify(notes, never()).saveAndFlush(any(Note.class));
    }

    @Test
    void attachmentUpdateLocksTimeEntryBeforeNoteToMatchTimeEntryDeletion() throws Exception {
        Note saved = note();
        UUID entryId = UUID.randomUUID();
        TimeEntry entry = new TimeEntry();
        entry.setId(entryId);
        entry.setUserId(USER);
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));
        when(entries.findByIdAndUserId(entryId, USER)).thenReturn(Optional.of(entry));
        when(notes.findForUpdateByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(USER, saved.getId(), new UpdateNoteRequest(
                "Attached", request("x", "body").contentJson(), entryId, saved.getVersion()));

        InOrder order = org.mockito.Mockito.inOrder(entries, notes);
        order.verify(entries).findByIdAndUserId(entryId, USER);
        order.verify(notes).findForUpdateByIdAndUserId(saved.getId(), USER);
    }

    @Test
    void staleAttachmentUpdatePreservesVersionConflictBeforeAttachmentLookup() throws Exception {
        Note saved = note();
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.update(USER, saved.getId(), new UpdateNoteRequest(
                "Stale", request("x", "body").contentJson(), UUID.randomUUID(), saved.getVersion() + 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("changed");
        verify(entries, never()).findByIdAndUserId(any(), eq(USER));
    }

    @Test
    void existingNoteCanBeClearedWithoutCreateMeaningfulness() throws Exception {
        Note saved = note();
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));
        when(notes.findForUpdateByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateNoteRequest clear = new UpdateNoteRequest(null, request("Title", "").contentJson(), null, 1);

        var result = service.update(USER, saved.getId(), clear);

        assertThat(result.title()).isNull();
        assertThat(result.contentText()).isEmpty();
    }

    @Test
    void changedPayloadWithSameKeyIsRejectedWithoutEchoingPayload() throws Exception {
        Note saved = note();
        when(replays.findByOwnerIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(replay(saved, null)));

        assertThatThrownBy(() -> service.create(USER, KEY, request("different", "text")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The creation key was already used");
        verify(notes, never()).saveAndFlush(any(Note.class));
    }

    @Test
    void titleLimitCountsUnicodeCodePointsExactly() throws Exception {
        String title = "😀".repeat(120);
        when(notes.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(USER, KEY, request(title, "" )).note().title()).isEqualTo(title);
        assertThatThrownBy(() -> service.create(USER, UUID.randomUUID(), request("😀".repeat(121), "")))
                .isInstanceOf(com.rotrack.exception.ValidationException.class)
                .hasMessage("title exceeds the maximum length");
    }

    @Test
    void previewIsLimitedToExactly160UnicodeCodePoints() {
        Note saved = note();
        String content = "a".repeat(159) + "😀" + "tail";
        saved.setContentText(content);
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));

        String preview = service.get(USER, saved.getId()).preview();

        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(160);
        assertThat(preview).endsWith("😀");
        assertThat(preview).doesNotContain("tail");
    }

    @Test
    void previewsCollapseUnicodeWhitespace() {
        Note saved = note();
        saved.setContentText("a\u00a0\u2003\u202f b");
        when(notes.findByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));

        assertThat(service.get(USER, saved.getId()).preview()).isEqualTo("a b");
    }

    @Test
    void validCursorPaginatesAfterItsCanonicalOrderingAnchor() {
        Instant updatedAt = Instant.parse("2026-01-01T10:00:00Z");
        UUID cursorId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        String payload = updatedAt + "|" + cursorId;
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        java.util.List<Note> rows = java.util.stream.IntStream.range(0, 21).mapToObj(index -> {
            Note row = note();
            row.setId(UUID.randomUUID());
            row.setUpdatedAt(updatedAt.minusSeconds(index + 1L));
            return row;
        }).toList();
        when(notes.findSummariesAfter(eq(USER), isNull(), isNull(), eq(updatedAt), eq(cursorId), any()))
                .thenReturn(rows);

        var page = service.list(USER, cursor, null, null);

        assertThat(page.notes()).hasSize(20);
        assertThat(page.nextCursor()).isNotBlank();
        verify(notes).findSummariesAfter(eq(USER), isNull(), isNull(), eq(updatedAt), eq(cursorId),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 21));
    }

    @Test
    void blankPaddedMalformedAndNonCanonicalCursorsReturnInvalidCursor() {
        UUID id = UUID.fromString("abcdefab-cdef-4abc-8def-abcdefabcdef");
        String canonicalPayload = "2026-01-01T10:00:00Z|" + id;
        String padded = Base64.getUrlEncoder().encodeToString(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        if (!padded.endsWith("=")) padded += "=";
        String nonCanonicalTimestamp = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("2026-01-01T10:00:00+00:00|" + id).getBytes(StandardCharsets.UTF_8));
        String nonCanonicalUuid = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("2026-01-01T10:00:00Z|" + id.toString().toUpperCase()).getBytes(StandardCharsets.UTF_8));

        String[] invalidCursors = {"", "not-a-cursor", padded, nonCanonicalTimestamp, nonCanonicalUuid};
        for (int index = 0; index < invalidCursors.length; index++) {
            try {
                service.list(USER, invalidCursors[index], null, null);
                throw new AssertionError("invalid cursor case " + index + " was accepted");
            } catch (com.rotrack.exception.InvalidCursorException expected) {
                // Expected stable validation boundary.
            }
        }
    }

    @Test
    void deletedReplayAndRepeatedMatchingDeleteAreStable() throws Exception {
        Note saved = note();
        when(notes.findForUpdateByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.of(saved));
        when(replays.findByOwnerIdAndNoteId(USER, saved.getId())).thenReturn(Optional.of(replay(saved, null)));
        service.delete(USER, saved.getId(), 1);

        ArgumentCaptor<NoteCreationReplay> captor = ArgumentCaptor.forClass(NoteCreationReplay.class);
        verify(replays).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDeletedVersion()).isEqualTo(1L);

        when(replays.findByOwnerIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(replay(saved, 1L)));
        assertThatThrownBy(() -> service.create(USER, KEY, request("Title", "")))
                .isInstanceOf(NoteDeletedException.class);

        when(notes.findForUpdateByIdAndUserId(saved.getId(), USER)).thenReturn(Optional.empty());
        when(replays.findByOwnerIdAndNoteId(USER, saved.getId())).thenReturn(Optional.of(replay(saved, 1L)));
        service.delete(USER, saved.getId(), 1);
    }

    private NoteRequest request(String title, String text) throws Exception {
        String content = text.isEmpty()
                ? "[]"
                : "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}]";
        return new NoteRequest(title, new ObjectMapper().readTree(
                "{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":" + content + "}}"), null);
    }

    private Note note() {
        Note note = new Note();
        note.setId(UUID.fromString("33333333-3333-4333-8333-333333333333"));
        note.setUserId(USER);
        note.setTitle("Title");
        note.setVersion(1);
        note.setContentText("");
        note.setContentJson(new ObjectMapper().createObjectNode());
        note.setContentSchemaVersion(1);
        return note;
    }

    private Note captureNote() {
        return org.mockito.Mockito.mockingDetails(notes).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveAndFlush"))
                .map(invocation -> (Note) invocation.getArgument(0))
                .findFirst().orElseThrow();
    }

    private NoteCreationReplay replay(Note note, Long deletedVersion) {
        NoteCreationReplay replay = new NoteCreationReplay();
        replay.setOwnerId(USER);
        replay.setIdempotencyKey(KEY);
        replay.setNoteId(note.getId());
        replay.setFingerprint(new NoteHmac("test-secret-test-secret-test-secret-test-secret")
                .fingerprint("Title", "{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[]}}", null));
        replay.setDeletedVersion(deletedVersion);
        return replay;
    }
}
