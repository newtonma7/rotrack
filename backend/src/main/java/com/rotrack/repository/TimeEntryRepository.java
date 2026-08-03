package com.rotrack.repository;

import com.rotrack.model.TimeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
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

    List<TimeEntry> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
            UUID userId,
            java.time.Instant start,
            java.time.Instant end
    );

    @Query(value = """
            SELECT activity_type,
                   COALESCE(SUM(FLOOR(EXTRACT(EPOCH FROM (end_time - start_time)) / 60))::bigint, 0)
            FROM time_entries
            WHERE user_id = :userId
              AND end_time IS NOT NULL
              AND start_time >= :start
              AND start_time < :end
            GROUP BY activity_type
            """, nativeQuery = true)
    List<Object[]> sumDurationByActivityType(
            @Param("userId") UUID userId,
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end
    );
}
