package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.dto.HistoryPageDTO;
import com.rotrack.dto.NoteDTO;
import com.rotrack.dto.NoteRequest;
import com.rotrack.dto.UpdateNoteRequest;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.NoteDeletedException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.Note;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.TimeEntryRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Public NoteService/repository proof against a committed, isolated PostgreSQL migration. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "SUPABASE_JWKS_URI=https://example.test/jwks",
        "SUPABASE_ISSUER_URI=https://example.test/issuer",
        "SUPABASE_JWT_AUDIENCE=authenticated",
        "rotrack.notes.hmac-secret=test-only-notes-hmac-secret-32-bytes"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
class NoteServicePostgresIntegrationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private NoteService service;
    @Autowired private NoteRepository notes;
    @Autowired private TimeEntryRepository entries;
    @Autowired private TimeEntryService timeEntryService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @MockitoBean private DatabaseTlsValidator databaseTlsValidator;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        requireIsolatedTarget();
        registry.add("spring.datasource.url", () -> requiredEnvironment("ROTRACK_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> environmentOrDefault(
                "ROTRACK_TEST_DATABASE_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> environmentOrDefault(
                "ROTRACK_TEST_DATABASE_PASSWORD", ""));
    }

    @BeforeEach
    void createOwnedUsers() {
        jdbc.update("DELETE FROM auth.users WHERE id IN (?, ?)", OWNER, OTHER);
        insertUser(OWNER, "note_owner");
        insertUser(OTHER, "note_other");
    }

    @Test
    void taskListDocumentRoundTripsThroughCreateReadUpdateRepository() throws Exception {
        var created = service.create(OWNER, UUID.randomUUID(), taskRequest("before", false));

        var read = service.get(OWNER, created.note().id());
        assertThat(read.contentText()).isEqualTo("before");
        assertThat(read.contentJson().at("/document/content/0/content/0/attrs/checked").booleanValue()).isFalse();

        var updated = service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                null, taskRequest("after", true).contentJson(), null, created.note().version()));
        var reread = service.get(OWNER, updated.id());
        assertThat(reread.contentText()).isEqualTo("after");
        assertThat(reread.contentJson().at("/document/content/0/content/0/attrs/checked").booleanValue()).isTrue();
        assertThat(reread.version()).isEqualTo(2);
    }

    @Test
    void readsAndListsNotesWithoutAWriteTransaction() throws Exception {
        Note note = notes.saveAndFlush(note(null, "Read me", "readable", null));

        assertThat(service.get(OWNER, note.getId()).title()).isEqualTo("Read me");
        assertThat(service.list(OWNER, null, null, null).notes())
                .extracting(summary -> summary.id())
                .containsExactly(note.getId());
    }

    @Test
    void concurrentIdenticalCreatesPersistExactlyOneNote() throws Exception {
        UUID key = UUID.randomUUID();
        NoteRequest request = request("Same", "same");
        var results = concurrently(request, request, key);

        assertThat(results).allMatch(result -> result.error() == null);
        assertThat(results).extracting(result -> result.note().id()).containsExactly(results.getFirst().note().id(), results.getFirst().note().id());
        assertThat(countNotes()).isEqualTo(1);
        assertThat(countReplays()).isEqualTo(1);
    }

    @Test
    void claimLossWithChangedPayloadDoesNotLeaveAnOrphanNote() throws Exception {
        UUID key = UUID.randomUUID();
        var results = concurrentlyWithReplayClaimsBlocked(
                request("First", "first"), request("Second", "second"), key);

        assertThat(results).anyMatch(result -> result.error() == null);
        assertThat(results).anyMatch(result -> result.error() instanceof ConflictException);
        assertThat(countNotes()).isEqualTo(1);
        assertThat(countReplays()).isEqualTo(1);
    }

    @Test
    void deletedCreationReplayCannotRecreateTheNote() throws Exception {
        UUID key = UUID.randomUUID();
        var created = service.create(OWNER, key, request("Gone", "gone"));

        service.delete(OWNER, created.note().id(), created.note().version());

        assertThatThrownBy(() -> service.create(OWNER, key, request("Gone", "gone")))
                .isInstanceOf(NoteDeletedException.class);
        assertThat(countNotes()).isZero();
        assertThat(countReplays()).isEqualTo(1);
    }

    @Test
    void attachmentCanMoveDetachAndDetachWhenItsTimeEntryIsDeleted() throws Exception {
        TimeEntry first = entry("2026-08-07T10:00:00Z", "2026-08-07T11:00:00Z");
        TimeEntry second = entry("2026-08-07T12:00:00Z", "2026-08-07T13:00:00Z");
        NoteRequest attached = request("Attached", "context");
        attached = new NoteRequest(attached.title(), attached.contentJson(), first.getId());
        var created = service.create(OWNER, UUID.randomUUID(), attached);

        assertThat(service.list(OWNER, null, null, first.getId()).notes()).hasSize(1);
        var moved = service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                "Moved", attached.contentJson(), second.getId(), created.note().version()));
        assertThat(moved.timeEntryId()).isEqualTo(second.getId());
        assertThat(service.list(OWNER, null, null, first.getId()).notes()).isEmpty();

        var detached = service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                "Detached", attached.contentJson(), null, moved.version()));
        assertThat(detached.timeEntryId()).isNull();
        assertThat(service.list(OWNER, null, com.rotrack.dto.NoteAttachmentFilter.STANDALONE, null).notes()).hasSize(1);

        var reattached = service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                "Attached again", attached.contentJson(), second.getId(), detached.version()));
        assertThat(timeEntryService.listHistory(OWNER, null).entries().getFirst().attachedNoteCount()).isEqualTo(1);
        timeEntryService.deleteEntry(OWNER, second.getId());
        assertThat(service.get(OWNER, reattached.id()).timeEntryId()).isNull();
        HistoryPageDTO remaining = timeEntryService.listHistory(OWNER, null);
        assertThat(remaining.entries()).hasSize(1);
        assertThat(remaining.entries().getFirst().attachedNoteCount()).isZero();
    }

    @Test
    void attachmentUpdateAndTimeEntryDeleteRaceLeavesTheNoteDetached() throws Exception {
        TimeEntry entry = entry("2026-08-07T14:00:00Z", "2026-08-07T15:00:00Z");
        NoteRequest attached = new NoteRequest("Race", request("x", "race").contentJson(), entry.getId());
        var created = service.create(OWNER, UUID.randomUUID(), attached);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement(
                    "SELECT id FROM public.time_entries WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, entry.getId());
                lock.executeQuery();
            }
            Future<Result> update = executor.submit(() -> invokeUpdate(ready, go, created.note().id(), entry.getId(), created.note().version()));
            Future<Throwable> delete = executor.submit(() -> invokeDelete(ready, go, entry.getId()));

            await(ready);
            go.countDown();
            assertThat(awaitTransactionLockWaiters()).isGreaterThanOrEqualTo(2L);
            blocker.commit();
            Result updateResult = get(update);
            Throwable deleteError = get(delete);

            assertThat(deleteError).isNull();
            assertThat(updateResult.error() == null || updateResult.error() instanceof ResourceNotFoundException).isTrue();
            assertThat(String.valueOf(updateResult.error())).doesNotContain("40P01", "deadlock", "DataIntegrityViolation");
            assertThat(String.valueOf(deleteError)).doesNotContain("40P01", "deadlock", "DataIntegrityViolation");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.time_entries WHERE id = ?", Long.class, entry.getId()))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT time_entry_id FROM public.notes WHERE id = ?", UUID.class, created.note().id()))
                    .isNull();
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("attachment race workers did not stop");
            }
        }
    }

    @Test
    void staleUpdateAndDeleteConflictAndOwnershipIsHidden() throws Exception {
        var created = service.create(OWNER, UUID.randomUUID(), request("Versioned", "body"));
        var updated = service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                "New", request("x", "new body").contentJson(), null, created.note().version()));

        assertThatThrownBy(() -> service.update(OWNER, created.note().id(), new UpdateNoteRequest(
                "Stale", request("x", "stale").contentJson(), null, created.note().version())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("changed");
        assertThatThrownBy(() -> service.delete(OWNER, created.note().id(), created.note().version()))
                .isInstanceOf(ConflictException.class);
        assertThat(service.get(OWNER, created.note().id()).version()).isEqualTo(updated.version());
        assertThatThrownBy(() -> service.get(OTHER, created.note().id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(OTHER, created.note().id(), new UpdateNoteRequest(
                "No", request("x", "no").contentJson(), null, updated.version())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private java.util.List<Result> concurrently(NoteRequest first, NoteRequest second, UUID key) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Result> left = executor.submit(() -> invoke(ready, go, first, key));
            Future<Result> right = executor.submit(() -> invoke(ready, go, second, key));
            await(ready);
            go.countDown();
            return java.util.List.of(get(left), get(right));
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent create workers did not stop");
            }
        }
    }

    private Result invoke(CountDownLatch ready, CountDownLatch go, NoteRequest request, UUID key) {
        try {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("create workers were not released");
            return new Result(service.create(OWNER, key, request).note(), null);
        } catch (Throwable error) {
            return new Result(null, error);
        }
    }

    private java.util.List<Result> concurrentlyWithReplayClaimsBlocked(
            NoteRequest first, NoteRequest second, UUID key) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("LOCK TABLE public.note_creation_replays IN SHARE MODE");
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try {
                Future<Result> left = executor.submit(() -> invoke(ready, go, first, key));
                Future<Result> right = executor.submit(() -> invoke(ready, go, second, key));
                await(ready);
                go.countDown();
                assertThat(awaitTableLockWaiters("public.note_creation_replays")).isGreaterThanOrEqualTo(2L);
                connection.commit();
                return java.util.List.of(get(left), get(right));
            } finally {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("blocked create workers did not stop");
                }
            }
        }
    }

    private Result invokeUpdate(CountDownLatch ready, CountDownLatch go, UUID noteId, UUID entryId, long version) {
        try {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("update worker was not released");
            NoteRequest request = request("Updated", "race update");
            return new Result(service.update(OWNER, noteId,
                    new UpdateNoteRequest(request.title(), request.contentJson(), entryId, version)), null);
        } catch (Throwable error) {
            return new Result(null, error);
        }
    }

    private Throwable invokeDelete(CountDownLatch ready, CountDownLatch go, UUID entryId) {
        try {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("delete worker was not released");
            timeEntryService.deleteEntry(OWNER, entryId);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void await(CountDownLatch latch) throws Exception {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("workers did not reach synchronization point");
    }

    private long awaitTableLockWaiters(String table) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long waiters = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE relation = ?::regclass "
                            + "AND mode = 'RowExclusiveLock' AND NOT granted",
                    Long.class, table);
            if (waiters != null && waiters >= 2) return waiters;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("workers did not reach the PostgreSQL table lock wait");
    }

    private long awaitTransactionLockWaiters() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long waiters = jdbc.queryForObject(
                    "SELECT count(DISTINCT pid) FROM pg_locks WHERE NOT granted "
                            + "AND (locktype = 'transactionid' "
                            + "OR (locktype = 'tuple' AND relation = 'public.time_entries'::regclass))",
                    Long.class);
            if (waiters != null && waiters >= 2) return waiters;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("workers did not both reach the PostgreSQL row-lock wait");
    }

    private <T> T get(Future<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private NoteRequest request(String title, String text) throws Exception {
        JsonNode document = MAPPER.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"%s"}]}]}}
                """.formatted(text));
        return new NoteRequest(title, document, null);
    }

    private NoteRequest taskRequest(String text, boolean checked) throws Exception {
        JsonNode document = MAPPER.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":%s},"content":[
                      {"type":"paragraph"%s}
                    ]}
                  ]}
                ]}}
                """.formatted(checked, text.isEmpty() ? "" : ",\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]"));
        return new NoteRequest(null, document, null);
    }

    private Note note(UUID entryId, String title, String text, UUID userId) throws Exception {
        Note note = new Note();
        note.setId(UUID.randomUUID());
        note.setUserId(userId == null ? OWNER : userId);
        note.setTimeEntryId(entryId);
        note.setAttachmentOwnerId(entryId == null ? null : note.getUserId());
        note.setTitle(title);
        note.setContentJson(request(title, text).contentJson());
        note.setContentText(text);
        note.setContentSchemaVersion(1);
        note.setVersion(1);
        return note;
    }

    private TimeEntry entry(String start, String end) {
        TimeEntry entry = new TimeEntry();
        entry.setUserId(OWNER);
        entry.setActivityType(ActivityType.WORK);
        entry.setStartTime(Instant.parse(start));
        entry.setEndTime(Instant.parse(end));
        return entries.saveAndFlush(entry);
    }

    private long countNotes() {
        return jdbc.queryForObject("SELECT count(*) FROM public.notes WHERE user_id = ?", Long.class, OWNER);
    }

    private long countReplays() {
        return jdbc.queryForObject("SELECT count(*) FROM public.note_creation_replays WHERE owner_id = ?", Long.class, OWNER);
    }

    private void insertUser(UUID id, String username) {
        jdbc.update("INSERT INTO auth.users(id, email, raw_user_meta_data) VALUES (?, ?, ?::jsonb)",
                id, "note-" + username + "@example.invalid", "{\"username\":\"" + username + "\"}");
    }

    private static void requireIsolatedTarget() {
        if (!"true".equalsIgnoreCase(requiredEnvironment("ROTRACK_TEST_DATABASE_ISOLATED"))) {
            throw new IllegalStateException("ROTRACK_TEST_DATABASE_ISOLATED must be true after confirming the target is disposable");
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required when PostgreSQL integration is enabled");
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private record Result(NoteDTO note, Throwable error) {}
}
