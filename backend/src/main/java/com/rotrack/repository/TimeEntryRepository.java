package com.rotrack.repository;

import com.rotrack.model.TimeEntry;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
