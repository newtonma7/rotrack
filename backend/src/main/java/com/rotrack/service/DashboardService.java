package com.rotrack.service;

import com.rotrack.dto.DailyStatsDTO;
import com.rotrack.dto.DashboardRangeDTO;
import com.rotrack.dto.DashboardStatsDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final TimeEntryRepository timeEntryRepository;
    private final Clock clock;

    public DashboardService(TimeEntryRepository timeEntryRepository, Clock clock) {
        this.timeEntryRepository = timeEntryRepository;
        this.clock = clock;
    }

    public DashboardStatsDTO getStats(
            UUID userId,
            String timeZone,
            LocalDate requestedStart,
            LocalDate requestedEnd
    ) {
        ZoneId zoneId = parseTimeZone(timeZone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        LocalDate startDate;
        LocalDate endDate;
        if (requestedStart == null && requestedEnd == null) {
            startDate = today.minusDays(6);
            endDate = today.plusDays(1);
        } else if (requestedStart == null || requestedEnd == null) {
            throw new IllegalArgumentException("start and end must be provided together");
        } else {
            startDate = requestedStart;
            endDate = requestedEnd;
        }
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (rangeDays < 1 || rangeDays > 366) {
            throw new IllegalArgumentException("Dashboard range must contain between 1 and 366 local days");
        }
        Instant start = startDate.atStartOfDay(zoneId).toInstant();
        Instant end = endDate.atStartOfDay(zoneId).toInstant();

        List<TimeEntry> entries = timeEntryRepository.findCompletedOverlappingRange(userId, start, end);
        Map<LocalDate, DailyTotals> buckets = emptyBuckets(startDate, endDate);
        Map<ActivityType, Long> totals = emptyTotals();

        for (TimeEntry entry : entries) {
            aggregateCompletedEntry(entry, start, end, zoneId, buckets, totals);
        }

        List<DailyStatsDTO> daily = buckets.entrySet().stream()
                .map(bucket -> new DailyStatsDTO(
                        bucket.getKey(),
                        bucket.getValue().workSeconds(),
                        bucket.getValue().rotSeconds()
                ))
                .toList();
        List<TimeEntryDTO> recentSessions = entries.stream()
                .filter(entry -> entry.getEndTime() != null)
                .sorted((left, right) -> right.getEndTime().compareTo(left.getEndTime()))
                .limit(10)
                .map(this::toDto)
                .toList();
        long trackedSeconds = totals.get(ActivityType.WORK) + totals.get(ActivityType.ROT);
        int productivityScore = trackedSeconds == 0
                ? 0
                : Math.toIntExact(Math.round(totals.get(ActivityType.WORK) * 100.0 / trackedSeconds));

        return new DashboardStatsDTO(
                new DashboardRangeDTO(start, end, zoneId.getId()),
                totals,
                daily,
                recentSessions,
                productivityScore
        );
    }

    private void aggregateCompletedEntry(
            TimeEntry entry,
            Instant rangeStart,
            Instant rangeEnd,
            ZoneId zoneId,
            Map<LocalDate, DailyTotals> buckets,
            Map<ActivityType, Long> totals
    ) {
        if (entry.getEndTime() == null) {
            return;
        }
        Instant cursor = entry.getStartTime().isAfter(rangeStart) ? entry.getStartTime() : rangeStart;
        Instant clippedEnd = entry.getEndTime().isBefore(rangeEnd) ? entry.getEndTime() : rangeEnd;

        // Local midnights can be 23 or 25 hours apart. Splitting at the zone's
        // next start-of-day preserves real elapsed seconds across DST changes.
        while (cursor.isBefore(clippedEnd)) {
            LocalDate localDate = cursor.atZone(zoneId).toLocalDate();
            Instant nextDay = localDate.plusDays(1).atStartOfDay(zoneId).toInstant();
            Instant segmentEnd = nextDay.isBefore(clippedEnd) ? nextDay : clippedEnd;
            long seconds = Duration.between(cursor, segmentEnd).getSeconds();
            DailyTotals bucket = buckets.get(localDate);
            if (bucket != null) {
                bucket.add(entry.getActivityType(), seconds);
                totals.merge(entry.getActivityType(), seconds, Long::sum);
            }
            cursor = segmentEnd;
        }
    }

    private Map<LocalDate, DailyTotals> emptyBuckets(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, DailyTotals> buckets = new LinkedHashMap<>();
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            buckets.put(date, new DailyTotals());
        }
        return buckets;
    }

    private Map<ActivityType, Long> emptyTotals() {
        Map<ActivityType, Long> totals = new EnumMap<>(ActivityType.class);
        totals.put(ActivityType.WORK, 0L);
        totals.put(ActivityType.ROT, 0L);
        return totals;
    }

    private TimeEntryDTO toDto(TimeEntry entry) {
        return new TimeEntryDTO(
                entry.getId(),
                entry.getActivityType(),
                entry.getStartTime(),
                entry.getEndTime(),
                Duration.between(entry.getStartTime(), entry.getEndTime()).getSeconds(),
                entry.getNotes()
        );
    }

    private static final class DailyTotals {
        private long workSeconds;
        private long rotSeconds;

        void add(ActivityType activityType, long seconds) {
            switch (activityType) {
                case WORK -> workSeconds += seconds;
                case ROT -> rotSeconds += seconds;
            }
        }

        long workSeconds() {
            return workSeconds;
        }

        long rotSeconds() {
            return rotSeconds;
        }
    }

    private ZoneId parseTimeZone(String timeZone) {
        try {
            if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone)) {
                throw new IllegalArgumentException("timeZone must be a valid IANA identifier");
            }
            return ZoneId.of(timeZone);
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("timeZone must be a valid IANA identifier", exception);
        }
    }
}
