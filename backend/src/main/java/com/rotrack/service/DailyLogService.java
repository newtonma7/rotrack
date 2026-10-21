package com.rotrack.service;

import com.rotrack.config.NoteHmac;
import com.rotrack.dto.DailyLogDTO;
import com.rotrack.dto.ReflectionDTO;
import com.rotrack.dto.UpdateReflectionRequest;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.exception.ValidationException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.Note;
import com.rotrack.model.Reflection;
import com.rotrack.model.ReflectionCreationReplay;
import com.rotrack.model.TimeEntry;
import com.rotrack.model.UserPreferences;
import com.rotrack.repository.NoteRepository;
import com.rotrack.repository.ReflectionCreationReplayRepository;
import com.rotrack.repository.ReflectionRepository;
import com.rotrack.repository.TimeEntryRepository;
import com.rotrack.repository.UserPreferencesRepository;
import com.rotrack.richtext.RichTextDocumentValidator;
import com.rotrack.richtext.RichTextValidationException;
import com.rotrack.richtext.RichTextValue;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyLogService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final TimeEntryRepository timeEntryRepository;
    private final NoteRepository noteRepository;
    private final ReflectionRepository reflectionRepository;
    private final ReflectionCreationReplayRepository replayRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final RichTextDocumentValidator validator;
    private final NoteHmac hmac;

    public DailyLogService(
            TimeEntryRepository timeEntryRepository,
            NoteRepository noteRepository,
            ReflectionRepository reflectionRepository,
            ReflectionCreationReplayRepository replayRepository,
            UserPreferencesRepository preferencesRepository,
            RichTextDocumentValidator validator,
            NoteHmac hmac
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.noteRepository = noteRepository;
        this.reflectionRepository = reflectionRepository;
        this.replayRepository = replayRepository;
        this.preferencesRepository = preferencesRepository;
        this.validator = validator;
        this.hmac = hmac;
    }

    public DailyLogDTO.SummaryPage summaries(UUID userId, String rawStart, String rawEnd, String requestedTimeZone) {
        LocalDate start = parseDate(rawStart, "start");
        LocalDate end = parseDate(rawEnd, "end");
        if (start == null || end == null) {
            throw validation("start and end must be supplied together", "start");
        }
        long days = end.toEpochDay() - start.toEpochDay();
        if (days < 1 || days > 42) {
            throw validation("range must contain 1 through 42 days", "end");
        }

        ZoneId zone = resolveTimeZone(userId, requestedTimeZone);
        Instant rangeStart = start.atStartOfDay(zone).toInstant();
        Instant rangeEnd = end.atStartOfDay(zone).toInstant();
        List<TimeEntry> sessions = timeEntryRepository.findCompletedOverlappingRange(userId, rangeStart, rangeEnd);
        List<LocalDate> dates = datesInRange(start, end);
        Map<LocalDate, DayTotals> totals = new HashMap<>();
        Map<UUID, TimeEntry> sessionsById = new HashMap<>();

        for (TimeEntry session : sessions) {
            sessionsById.put(session.getId(), session);
            accumulateSession(session, dates, zone, totals);
        }
        accumulateNoteCounts(userId, dates, zone, sessionsById, totals);

        Map<LocalDate, Boolean> reflectionContentByDate = new HashMap<>();
        reflectionRepository.findSummaries(userId, start, end.minusDays(1)).forEach(reflection ->
                reflectionContentByDate.put(reflection.getLocalDate(), reflection.getHasContent()));

        List<DailyLogDTO.Summary> summaries = dates.stream()
                .map(date -> toSummary(date, totals.getOrDefault(date, new DayTotals()),
                        reflectionContentByDate.getOrDefault(date, false)))
                .toList();
        return new DailyLogDTO.SummaryPage(new DailyLogDTO.Range(start, end, zone.getId()), summaries);
    }

    public DailyLogDTO.Detail detail(UUID userId, String rawDate, String requestedTimeZone) {
        LocalDate date = parseDate(rawDate, "localDate");
        ZoneId zone = resolveTimeZone(userId, requestedTimeZone);
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<TimeEntry> overlapping = timeEntryRepository.findCompletedOverlappingRange(userId, dayStart, dayEnd);
        DayProjection projection = projectDay(overlapping, dayStart, dayEnd);
        List<DailyLogDTO.NoteReference> noteReferences = noteReferences(userId, projection.sessionsById(), dayStart, dayEnd);
        Reflection reflection = reflectionRepository.findByUserIdAndLocalDate(userId, date).orElse(null);
        DailyLogDTO.Generated generated = new DailyLogDTO.Generated(
                projection.workSeconds(), projection.rotSeconds(), projection.sessions(), noteReferences);
        return new DailyLogDTO.Detail(date, zone.getId(), generated, reflection == null ? null : toDto(reflection));
    }

    @Transactional
    public ReflectionDTO updateReflection(
            UUID userId,
            String rawDate,
            String requestedTimeZone,
            UUID idempotencyKey,
            UpdateReflectionRequest request
    ) {
        hmac.requireWritesEnabled();
        LocalDate date = parseDate(rawDate, "localDate");
        ZoneId zone = resolveTimeZone(userId, requestedTimeZone);
        RichTextValue value = validateDocument(request);
        if (request.containsUnknownProperties()) {
            throw validation("Request contains unknown fields", "body");
        }
        if (request.expectedVersion() == null) {
            throw validation("expectedVersion is required", "expectedVersion");
        }

        if (request.expectedVersion() == 0) {
            return createReflection(userId, date, zone, idempotencyKey, value);
        }
        if (idempotencyKey != null) {
            throw validation("Idempotency-Key is only valid for creation", "Idempotency-Key");
        }
        if (request.expectedVersion() < 0) {
            throw validation("expectedVersion must be nonnegative", "expectedVersion");
        }

        Reflection reflection = reflectionRepository.lockByOwnerAndDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Reflection not found"));
        if (reflection.getVersion() != request.expectedVersion()) {
            throw versionConflict();
        }
        applyDocument(reflection, value, reflection.getVersion() + 1);
        return toDto(reflectionRepository.saveAndFlush(reflection));
    }

    private void accumulateSession(TimeEntry session, List<LocalDate> dates, ZoneId zone, Map<LocalDate, DayTotals> totals) {
        for (LocalDate date : dates) {
            Instant segmentStart = later(session.getStartTime(), date.atStartOfDay(zone).toInstant());
            Instant segmentEnd = earlier(session.getEndTime(), date.plusDays(1).atStartOfDay(zone).toInstant());
            if (segmentStart.isBefore(segmentEnd)) {
                DayTotals day = totals.computeIfAbsent(date, ignored -> new DayTotals());
                day.add(session.getActivityType(), Duration.between(segmentStart, segmentEnd).getSeconds());
                day.sessionCount++;
            }
        }
    }

    private void accumulateNoteCounts(
            UUID userId,
            List<LocalDate> dates,
            ZoneId zone,
            Map<UUID, TimeEntry> sessionsById,
            Map<LocalDate, DayTotals> totals
    ) {
        if (sessionsById.isEmpty()) {
            return;
        }
        Map<LocalDate, Set<UUID>> noteIdsByDate = new HashMap<>();
        for (NoteRepository.DailyNoteCountProjection note :
                noteRepository.findDailyNoteCounts(userId, new ArrayList<>(sessionsById.keySet()))) {
            TimeEntry session = sessionsById.get(note.getTimeEntryId());
            if (session == null) {
                continue;
            }
            for (LocalDate date : dates) {
                if (intersectsDay(session, date, zone)) {
                    noteIdsByDate.computeIfAbsent(date, ignored -> new HashSet<>()).add(note.getId());
                }
            }
        }
        noteIdsByDate.forEach((date, ids) -> totals.computeIfAbsent(date, ignored -> new DayTotals()).noteCount = ids.size());
    }

    private boolean intersectsDay(TimeEntry session, LocalDate date, ZoneId zone) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        return session.getStartTime().isBefore(dayEnd) && session.getEndTime().isAfter(dayStart);
    }

    private DayProjection projectDay(List<TimeEntry> overlapping, Instant dayStart, Instant dayEnd) {
        List<DailyLogDTO.Session> sessions = new ArrayList<>();
        Map<UUID, TimeEntry> sessionsById = new HashMap<>();
        long workSeconds = 0;
        long rotSeconds = 0;
        for (TimeEntry session : overlapping) {
            Instant segmentStart = later(session.getStartTime(), dayStart);
            Instant segmentEnd = earlier(session.getEndTime(), dayEnd);
            if (!segmentStart.isBefore(segmentEnd)) {
                continue;
            }
            long seconds = Duration.between(segmentStart, segmentEnd).getSeconds();
            if (session.getActivityType() == ActivityType.WORK) {
                workSeconds += seconds;
            } else {
                rotSeconds += seconds;
            }
            sessions.add(new DailyLogDTO.Session(session.getId().toString(), session.getActivityType().name(),
                    segmentStart, segmentEnd, seconds, session.getNotes()));
            sessionsById.put(session.getId(), session);
        }
        return new DayProjection(workSeconds, rotSeconds, sessions, sessionsById);
    }

    private List<DailyLogDTO.NoteReference> noteReferences(
            UUID userId, Map<UUID, TimeEntry> sessionsById, Instant dayStart, Instant dayEnd
    ) {
        if (sessionsById.isEmpty()) {
            return List.of();
        }
        return noteRepository.findDailyNotePreviews(userId, new ArrayList<>(sessionsById.keySet())).stream()
                .filter(note -> sessionsById.containsKey(note.getTimeEntryId()))
                .sorted(Comparator
                        .comparing((NoteRepository.DailyNotePreviewProjection note) ->
                                later(sessionsById.get(note.getTimeEntryId()).getStartTime(), dayStart))
                        .thenComparing(NoteRepository.DailyNotePreviewProjection::getUpdatedAt)
                        .thenComparing(NoteRepository.DailyNotePreviewProjection::getId))
                .map(note -> new DailyLogDTO.NoteReference(note.getId().toString(), note.getTitle(),
                        preview(note.getContentText()), note.getTimeEntryId().toString()))
                .toList();
    }

    private ReflectionDTO createReflection(UUID userId, LocalDate date, ZoneId zone, UUID key, RichTextValue value) {
        if (key == null) {
            throw validation("Idempotency-Key must be a canonical UUID", "Idempotency-Key");
        }
        if (!value.meaningful()) {
            throw validation("Reflection must contain meaningful text when created", "contentJson");
        }
        byte[] fingerprint = hmac.reflectionFingerprint(date.toString(), zone.getId(), value.serialized());
        Optional<ReflectionCreationReplay> previous = replayRepository.findByOwnerIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            return replay(userId, date, fingerprint, previous.get());
        }

        UUID reflectionId = UUID.randomUUID();
        if (replayRepository.claim(userId, key, fingerprint, reflectionId) == 0) {
            ReflectionCreationReplay winner = replayRepository.findByOwnerIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> new IllegalStateException("Reflection creation replay claim disappeared"));
            return replay(userId, date, fingerprint, winner);
        }
        if (reflectionRepository.findByUserIdAndLocalDate(userId, date).isPresent()) {
            throw versionConflict();
        }

        Reflection reflection = new Reflection();
        reflection.setId(reflectionId);
        reflection.setUserId(userId);
        reflection.setLocalDate(date);
        reflection.setTimeZoneAtCreation(zone.getId());
        applyDocument(reflection, value, 1);
        try {
            return toDto(reflectionRepository.saveAndFlush(reflection));
        } catch (DataIntegrityViolationException exception) {
            throw versionConflict();
        }
    }

    private ReflectionDTO replay(UUID userId, LocalDate date, byte[] fingerprint, ReflectionCreationReplay replay) {
        if (!hmac.matches(fingerprint, replay.getFingerprint())) {
            throw new ConflictException("IDEMPOTENCY_CONFLICT", "The creation key was already used");
        }
        Reflection current = reflectionRepository.findByUserIdAndLocalDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Reflection not found"));
        if (!current.getId().equals(replay.getReflectionId())) {
            throw new ConflictException("IDEMPOTENCY_CONFLICT", "The creation key was already used");
        }
        return toDto(current);
    }

    private RichTextValue validateDocument(UpdateReflectionRequest request) {
        if (request == null) {
            throw validation("Request body is required", "body");
        }
        try {
            return validator.validate(request.contentJson());
        } catch (RichTextValidationException exception) {
            throw validation("contentJson is invalid", "contentJson");
        }
    }

    private void applyDocument(Reflection reflection, RichTextValue value, long version) {
        reflection.setContentJson(value.contentJson());
        reflection.setContentText(value.contentText());
        reflection.setContentSchemaVersion(1);
        reflection.setVersion(version);
    }

    private DailyLogDTO.Summary toSummary(LocalDate date, DayTotals totals, boolean hasReflectionContent) {
        return new DailyLogDTO.Summary(date, totals.workSeconds, totals.rotSeconds,
                totals.sessionCount, totals.noteCount, hasReflectionContent);
    }

    private List<LocalDate> datesInRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private ZoneId resolveTimeZone(UUID userId, String requestedTimeZone) {
        String timeZone = requestedTimeZone;
        if (timeZone == null) {
            timeZone = preferencesRepository.findById(userId).map(UserPreferences::getTimezone).orElse(null);
        }
        if (timeZone == null) {
            throw validation("timeZone is required when no saved timezone exists", "timeZone");
        }
        try {
            return TimeZoneValidator.parse(timeZone);
        } catch (IllegalArgumentException exception) {
            throw validation("timeZone must be a valid IANA identifier", "timeZone");
        }
    }

    private LocalDate parseDate(String rawDate, String field) {
        if (rawDate == null || !rawDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw validation("date must use canonical YYYY-MM-DD with year 0001 through 9999", field);
        }
        try {
            LocalDate date = LocalDate.parse(rawDate, DATE_FORMAT);
            if (date.getYear() < 1 || date.getYear() > 9999) {
                throw new java.time.DateTimeException("date out of bounds");
            }
            return date;
        } catch (java.time.DateTimeException exception) {
            throw validation("date must use canonical YYYY-MM-DD with year 0001 through 9999", field);
        }
    }

    private ReflectionDTO toDto(Reflection reflection) {
        return new ReflectionDTO(reflection.getContentJson(), reflection.getContentText(),
                reflection.getContentSchemaVersion(), reflection.getVersion(),
                reflection.getTimeZoneAtCreation(), reflection.getUpdatedAt());
    }

    private String preview(String contentText) {
        String collapsed = contentText.replaceAll("[\\p{javaWhitespace}\\p{Z}]+", " ").strip();
        int codePoints = collapsed.codePointCount(0, collapsed.length());
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, Math.min(160, codePoints)));
    }

    private ValidationException validation(String message, String field) {
        return new ValidationException(message, Map.of(field, message));
    }

    private ConflictException versionConflict() {
        return new ConflictException("RICH_TEXT_VERSION_CONFLICT",
                "The Reflection has changed; reload or preserve your local edits");
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static final class DayTotals {
        private long workSeconds;
        private long rotSeconds;
        private long sessionCount;
        private long noteCount;

        private void add(ActivityType activityType, long seconds) {
            if (activityType == ActivityType.WORK) {
                workSeconds += seconds;
            } else {
                rotSeconds += seconds;
            }
        }
    }

    private record DayProjection(
            long workSeconds,
            long rotSeconds,
            List<DailyLogDTO.Session> sessions,
            Map<UUID, TimeEntry> sessionsById
    ) {
    }
}
