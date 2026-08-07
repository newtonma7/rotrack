package com.rotrack.service;

import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;

    public TimeEntryService(TimeEntryRepository timeEntryRepository) {
        this.timeEntryRepository = timeEntryRepository;
    }

    @Transactional
    public TimeEntryDTO startSession(UUID userId, ActivityType activityType, String notes) {
        timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .ifPresent(active -> {
                    throw new ConflictException(
                            "ACTIVE_SESSION_EXISTS",
                            "An active session already exists"
                    );
                });

        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setActivityType(activityType);
        entry.setStartTime(Instant.now());
        entry.setNotes(notes);
        try {
            // The partial unique index is the final race-safe authority when two starts
            // pass the read check at the same time; translate its failure into the API contract.
            return toDto(timeEntryRepository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException exception) {
            if (isActiveSessionConstraint(exception)) {
                throw new ConflictException(
                        "ACTIVE_SESSION_EXISTS",
                        "An active session already exists"
                );
            }
            throw exception;
        }
    }

    @Transactional
    public TimeEntryDTO stopSession(UUID userId, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Time entry not found"));

        if (entry.getEndTime() == null) {
            entry.setEndTime(Instant.now());
            return toDto(timeEntryRepository.save(entry));
        }

        // Retries return the persisted resource, preserving the original server timestamp.
        return toDto(entry);
    }

    public TimeEntryDTO getActiveSession(UUID userId) {
        return timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .map(this::toDto)
                .orElse(null);
    }

    private boolean isActiveSessionConstraint(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("idx_time_entries_one_active_per_user");
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
}
