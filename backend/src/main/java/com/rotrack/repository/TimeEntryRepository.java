package com.rotrack.repository;

import com.rotrack.model.TimeEntry;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    /**
     * Serialize stop requests for one entry so a retry cannot replace the first
     * server-assigned end timestamp with a later one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TimeEntry> findByIdAndUserId(UUID id, UUID userId);

    Optional<TimeEntry> findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(UUID userId);

    @Query("""
            SELECT entry
            FROM TimeEntry entry
            WHERE entry.userId = :userId
              AND entry.endTime IS NOT NULL
            ORDER BY entry.startTime DESC, entry.id DESC
            """)
    List<TimeEntry> findCompletedHistory(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT entry
            FROM TimeEntry entry
            WHERE entry.userId = :userId
              AND entry.endTime IS NOT NULL
              AND (entry.startTime < :cursorStart
                   OR (entry.startTime = :cursorStart AND entry.id < :cursorId))
            ORDER BY entry.startTime DESC, entry.id DESC
            """)
    List<TimeEntry> findCompletedHistoryAfter(
            @Param("userId") UUID userId,
            @Param("cursorStart") Instant cursorStart,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    /**
     * Uses the same half-open range rule as the PostgreSQL exclusion constraint.
     * The database check remains authoritative when concurrent writers race this read.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM public.time_entries entry
              WHERE entry.user_id = :userId
                AND (CAST(:excludeId AS uuid) IS NULL OR entry.id <> CAST(:excludeId AS uuid))
                AND (CAST(:candidateEnd AS timestamptz) IS NULL OR entry.start_time < CAST(:candidateEnd AS timestamptz))
                AND (entry.end_time IS NULL OR entry.end_time > CAST(:candidateStart AS timestamptz))
            )
            """, nativeQuery = true)
    boolean existsOverlappingEntry(
            @Param("userId") UUID userId,
            @Param("candidateStart") Instant candidateStart,
            @Param("candidateEnd") Instant candidateEnd,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Select completed owned sessions that overlap the half-open report range.
     * Selecting overlaps—not only starts inside the range—allows boundary clipping.
     */
    @Query("""
            SELECT entry
            FROM TimeEntry entry
            WHERE entry.userId = :userId
              AND entry.endTime IS NOT NULL
              AND entry.startTime < :end
              AND entry.endTime > :start
            ORDER BY entry.startTime ASC
            """)
    List<TimeEntry> findCompletedOverlappingRange(
            @Param("userId") UUID userId,
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end
    );
}
