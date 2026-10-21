package com.rotrack.repository;

import com.rotrack.model.Note;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Note> findForUpdateByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT note FROM Note note
            WHERE note.userId = :userId
              AND (:attachment IS NULL
                   OR (:attachment = true AND note.timeEntryId IS NOT NULL)
                   OR (:attachment = false AND note.timeEntryId IS NULL))
              AND (:timeEntryId IS NULL OR note.timeEntryId = :timeEntryId)
            ORDER BY note.updatedAt DESC, note.id DESC
            """)
    List<Note> findSummaries(
            @Param("userId") UUID userId,
            @Param("attachment") Boolean attachment,
            @Param("timeEntryId") UUID timeEntryId,
            Pageable pageable
    );

    @Query("""
            SELECT note FROM Note note
            WHERE note.userId = :userId
              AND (:attachment IS NULL
                   OR (:attachment = true AND note.timeEntryId IS NOT NULL)
                   OR (:attachment = false AND note.timeEntryId IS NULL))
              AND (:timeEntryId IS NULL OR note.timeEntryId = :timeEntryId)
              AND (note.updatedAt < :cursorUpdated
                   OR (note.updatedAt = :cursorUpdated AND note.id < :cursorId))
            ORDER BY note.updatedAt DESC, note.id DESC
            """)
    List<Note> findSummariesAfter(
            @Param("userId") UUID userId,
            @Param("attachment") Boolean attachment,
            @Param("timeEntryId") UUID timeEntryId,
            @Param("cursorUpdated") Instant cursorUpdated,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    long countByUserIdAndTimeEntryId(UUID userId, UUID timeEntryId);

    @Query("select note.id as id, note.timeEntryId as timeEntryId from Note note "
            + "where note.userId = :userId and note.timeEntryId in :timeEntryIds")
    List<DailyNoteCountProjection> findDailyNoteCounts(
            @Param("userId") UUID userId, @Param("timeEntryIds") List<UUID> timeEntryIds);

    @Query("select note.id as id, note.title as title, note.contentText as contentText, "
            + "note.timeEntryId as timeEntryId, note.updatedAt as updatedAt from Note note "
            + "where note.userId = :userId and note.timeEntryId in :timeEntryIds")
    List<DailyNotePreviewProjection> findDailyNotePreviews(
            @Param("userId") UUID userId, @Param("timeEntryIds") List<UUID> timeEntryIds);

    interface DailyNoteCountProjection {
        UUID getId();
        UUID getTimeEntryId();
    }

    interface DailyNotePreviewProjection {
        UUID getId();
        String getTitle();
        String getContentText();
        UUID getTimeEntryId();
        Instant getUpdatedAt();
    }
}
