package com.rotrack.service;

import com.rotrack.dto.CompletedTimeEntryRequest;
import com.rotrack.dto.HistoryPageDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeEntryService {

    private static final int HISTORY_PAGE_SIZE = 20;
    private static final String OVERLAP_CONSTRAINT = "time_entries_no_overlap_per_user";

    private final TimeEntryRepository timeEntryRepository;
    private final Clock clock;

    public TimeEntryService(TimeEntryRepository timeEntryRepository) {
        this(timeEntryRepository, Clock.systemUTC());
    }

    @Autowired
    public TimeEntryService(TimeEntryRepository timeEntryRepository, Clock clock) {
        this.timeEntryRepository = timeEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public TimeEntryDTO startSession(UUID userId, ActivityType activityType, String notes) {
        validateNotes(notes);
        timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .ifPresent(active -> {
                    throw new ConflictException(
                            "ACTIVE_SESSION_EXISTS",
                            "An active session already exists"
                    );
                });

        Instant start = Instant.now(clock);
        if (timeEntryRepository.existsOverlappingEntry(userId, start, null, null)) {
            throw overlapConflict();
        }

        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setActivityType(activityType);
        entry.setStartTime(start);
        entry.setNotes(notes);
        try {
            // The exclusion constraint is the final race-safe authority when two writers pass the read check together.
            return toDto(timeEntryRepository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception, false);
        }
    }

    @Transactional
    public TimeEntryDTO stopSession(UUID userId, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));

        if (entry.getEndTime() == null) {
            entry.setEndTime(Instant.now(clock));
            try {
                return toDto(timeEntryRepository.saveAndFlush(entry));
            } catch (DataIntegrityViolationException exception) {
                throw translateConstraint(exception, false);
            }
        }

        // Retries return the persisted resource, preserving the original server timestamp.
        return toDto(entry);
    }

    public TimeEntryDTO getActiveSession(UUID userId) {
        return timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .map(this::toDto)
                .orElse(null);
    }

    public HistoryPageDTO listHistory(UUID userId, String cursor) {
        Cursor decoded = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, HISTORY_PAGE_SIZE + 1);
        List<TimeEntry> rows = decoded == null
                ? timeEntryRepository.findCompletedHistory(userId, page)
                : timeEntryRepository.findCompletedHistoryAfter(userId, decoded.startTime(), decoded.id(), page);
        boolean hasNext = rows.size() > HISTORY_PAGE_SIZE;
        List<TimeEntry> pageRows = hasNext ? rows.subList(0, HISTORY_PAGE_SIZE) : rows;
        String nextCursor = hasNext ? encodeCursor(pageRows.getLast()) : null;
        return new HistoryPageDTO(pageRows.stream().map(this::toDto).toList(), nextCursor);
    }

    @Transactional
    public TimeEntryDTO createCompletedEntry(
            UUID userId,
            ActivityType activityType,
            Instant startTime,
            Instant endTime,
            String notes
    ) {
        validateEntry(startTime, endTime, notes);
        if (timeEntryRepository.existsOverlappingEntry(userId, startTime, endTime, null)) {
            throw overlapConflict();
        }

        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setActivityType(activityType);
        entry.setStartTime(startTime);
        entry.setEndTime(endTime);
        entry.setNotes(notes);
        try {
            return toDto(timeEntryRepository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception, true);
        }
    }

    public TimeEntryDTO updateCompletedEntry(UUID userId, UUID entryId, CompletedTimeEntryRequest request) {
        return updateCompletedEntry(
                userId,
                entryId,
                request.activityType(),
                request.startTime(),
                request.endTime(),
                request.notes()
        );
    }

    @Transactional
    public TimeEntryDTO updateCompletedEntry(
            UUID userId,
            UUID entryId,
            ActivityType activityType,
            Instant startTime,
            Instant endTime,
            String notes
    ) {
        validateEntry(startTime, endTime, notes);
        TimeEntry entry = ownedCompletedEntry(userId, entryId);
        if (timeEntryRepository.existsOverlappingEntry(userId, startTime, endTime, entryId)) {
            throw overlapConflict();
        }

        entry.setActivityType(activityType);
        entry.setStartTime(startTime);
        entry.setEndTime(endTime);
        entry.setNotes(notes);
        try {
            return toDto(timeEntryRepository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception, true);
        }
    }

    @Transactional
    public void deleteEntry(UUID userId, UUID entryId) {
        TimeEntry entry = ownedCompletedEntry(userId, entryId);
        timeEntryRepository.delete(entry);
        timeEntryRepository.flush();
    }

    private TimeEntry ownedCompletedEntry(UUID userId, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));
        if (entry.getEndTime() == null) {
            throw new ResourceNotFoundException("Time entry not found");
        }
        return entry;
    }

    private void validateEntry(Instant startTime, Instant endTime, String notes) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        validateNotes(notes);
    }

    private void validateNotes(String notes) {
        if (notes != null && notes.length() > 280) {
            throw new IllegalArgumentException("notes must be 280 characters or fewer");
        }
    }

    private Cursor decodeCursor(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.isBlank()) {
                throw new IllegalArgumentException();
            }
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 2 || !value.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new InvalidCursorException();
        }
    }

    private String encodeCursor(TimeEntry entry) {
        String payload = entry.getStartTime() + "|" + entry.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private RuntimeException translateConstraint(DataIntegrityViolationException exception, boolean overlapExpected) {
        if (containsConstraint(exception, OVERLAP_CONSTRAINT)) {
            return overlapConflict();
        }
        if (!overlapExpected && containsConstraint(exception, "idx_time_entries_one_active_per_user")) {
            return new ConflictException("ACTIVE_SESSION_EXISTS", "An active session already exists");
        }
        return exception;
    }

    private boolean containsConstraint(DataIntegrityViolationException exception, String constraint) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ConflictException overlapConflict() {
        return new ConflictException("TIME_ENTRY_OVERLAP", "Time entry overlaps another entry");
    }

    private Long durationSeconds(TimeEntry entry) {
        if (entry.getEndTime() == null) {
            return null;
        }
        return Duration.between(entry.getStartTime(), entry.getEndTime()).getSeconds();
    }

    private TimeEntryDTO toDto(TimeEntry entry) {
        return new TimeEntryDTO(
                entry.getId(),
                entry.getActivityType(),
                entry.getStartTime(),
                entry.getEndTime(),
                durationSeconds(entry),
                entry.getNotes()
        );
    }

    private record Cursor(Instant startTime, UUID id) {
    }
}
