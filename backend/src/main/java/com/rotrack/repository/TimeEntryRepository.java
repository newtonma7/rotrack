package com.rotrack.repository;

import com.rotrack.model.TimeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByIdAndUserId(UUID id, UUID userId);

    Optional<TimeEntry> findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(UUID userId);

    List<TimeEntry> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
            UUID userId,
            java.time.Instant start,
            java.time.Instant end
    );

    @Query("""
            SELECT t.activityType, COALESCE(SUM(t.durationMinutes), 0)
            FROM TimeEntry t
            WHERE t.userId = :userId
              AND t.endTime IS NOT NULL
              AND t.startTime >= :start
              AND t.startTime < :end
            GROUP BY t.activityType
            """)
    List<Object[]> sumDurationByActivityType(
            @Param("userId") UUID userId,
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end
    );
}
