package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rotrack.dto.DashboardStatsDTO;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DashboardServiceTest {

    private final TimeEntryRepository repository = mock(TimeEntryRepository.class);
    private final UUID userId = UUID.randomUUID();

    @Test
    void emptyDefaultRangeContainsSevenCompleteLocalDays() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-10T16:00:00Z"), ZoneOffset.UTC);
        DashboardService service = new DashboardService(repository, clock);
        Instant expectedStart = Instant.parse("2026-03-04T05:00:00Z");
        Instant expectedEnd = Instant.parse("2026-03-11T04:00:00Z");
        when(repository.findCompletedOverlappingRange(userId, expectedStart, expectedEnd))
                .thenReturn(List.of());

        DashboardStatsDTO result = service.getStats(userId, "America/New_York", null, null);

        assertEquals(expectedStart, result.range().start());
        assertEquals(expectedEnd, result.range().end());
        assertEquals("America/New_York", result.range().timeZone());
        assertEquals(7, result.daily().size());
        assertEquals(LocalDate.parse("2026-03-04"), result.daily().getFirst().localDate());
        assertEquals(LocalDate.parse("2026-03-10"), result.daily().getLast().localDate());
        assertEquals(0L, result.totalSeconds().get(ActivityType.WORK));
        assertEquals(0L, result.totalSeconds().get(ActivityType.ROT));
        assertEquals(0, result.productivityScore());
        assertEquals(List.of(), result.recentSessions());
    }

    @Test
    void clipsAndSplitsSessionsAcrossRangeAndLocalDayBoundaries() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC);
        DashboardService service = new DashboardService(repository, clock);
        Instant rangeStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2026-01-03T00:00:00Z");
        TimeEntry workCrossingStart = entry(
                ActivityType.WORK,
                "2025-12-31T23:30:00Z",
                "2026-01-01T00:30:00Z"
        );
        TimeEntry rotCrossingMidnight = entry(
                ActivityType.ROT,
                "2026-01-01T23:30:00Z",
                "2026-01-02T00:30:00Z"
        );
        TimeEntry workCrossingEnd = entry(
                ActivityType.WORK,
                "2026-01-02T23:00:00Z",
                "2026-01-03T01:00:00Z"
        );
        TimeEntry active = entry(ActivityType.ROT, "2026-01-02T10:00:00Z", null);
        when(repository.findCompletedOverlappingRange(userId, rangeStart, rangeEnd))
                .thenReturn(List.of(workCrossingStart, rotCrossingMidnight, workCrossingEnd, active));

        DashboardStatsDTO result = service.getStats(
                userId,
                "UTC",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-03")
        );

        assertEquals(5_400L, result.totalSeconds().get(ActivityType.WORK));
        assertEquals(3_600L, result.totalSeconds().get(ActivityType.ROT));
        assertEquals(1_800L, result.daily().get(0).workSeconds());
        assertEquals(1_800L, result.daily().get(0).rotSeconds());
        assertEquals(3_600L, result.daily().get(1).workSeconds());
        assertEquals(1_800L, result.daily().get(1).rotSeconds());
        assertEquals(60, result.productivityScore());
        assertEquals(3, result.recentSessions().size());
        assertEquals(workCrossingEnd.getId(), result.recentSessions().getFirst().id());
        assertEquals(7_200L, result.recentSessions().getFirst().durationSeconds());
    }

    @Test
    void roundsProductivityScoreToTheNearestWholePercent() {
        DashboardService service = new DashboardService(
                repository,
                Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC)
        );
        Instant rangeStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2026-01-02T00:00:00Z");
        when(repository.findCompletedOverlappingRange(userId, rangeStart, rangeEnd))
                .thenReturn(List.of(
                        entry(ActivityType.WORK, "2026-01-01T00:00:00Z", "2026-01-01T00:00:01Z"),
                        entry(ActivityType.ROT, "2026-01-01T00:00:01Z", "2026-01-01T00:00:03Z")
                ));

        DashboardStatsDTO result = service.getStats(
                userId,
                "UTC",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-02")
        );

        assertEquals(33, result.productivityScore());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03-08, 2026-03-08T05:00:00Z, 2026-03-09T04:00:00Z, 82800",
            "2026-11-01, 2026-11-01T04:00:00Z, 2026-11-02T05:00:00Z, 90000"
    })
    void usesRealElapsedSecondsForDstTransitionDays(
            LocalDate localDate,
            Instant rangeStart,
            Instant rangeEnd,
            long expectedSeconds
    ) {
        DashboardService service = new DashboardService(
                repository,
                Clock.fixed(rangeStart, ZoneOffset.UTC)
        );
        TimeEntry work = entry(ActivityType.WORK, rangeStart.toString(), rangeEnd.toString());
        when(repository.findCompletedOverlappingRange(userId, rangeStart, rangeEnd))
                .thenReturn(List.of(work));

        DashboardStatsDTO result = service.getStats(
                userId,
                "America/New_York",
                localDate,
                localDate.plusDays(1)
        );

        assertEquals(expectedSeconds, result.daily().getFirst().workSeconds());
        assertEquals(expectedSeconds, result.totalSeconds().get(ActivityType.WORK));
    }

    @Test
    void acceptsTheOneAnd366DayRangeLimits() {
        DashboardService service = new DashboardService(
                repository,
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
        );

        DashboardStatsDTO oneDay = service.getStats(
                userId,
                "UTC",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-02")
        );
        DashboardStatsDTO threeHundredSixtySixDays = service.getStats(
                userId,
                "UTC",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-01-02")
        );

        assertEquals(1, oneDay.daily().size());
        assertEquals(366, threeHundredSixtySixDays.daily().size());
    }

    @Test
    void rejectsInvalidTimeZoneOrRange() {
        DashboardService service = new DashboardService(
                repository,
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getStats(userId, "Not/A_Zone", null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getStats(userId, "UTC", LocalDate.parse("2026-01-01"), null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getStats(
                        userId,
                        "UTC",
                        LocalDate.parse("2026-01-02"),
                        LocalDate.parse("2026-01-01")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getStats(
                        userId,
                        "UTC",
                        LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2026-01-03")
                )
        );
    }

    private TimeEntry entry(ActivityType type, String start, String end) {
        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setActivityType(type);
        entry.setStartTime(Instant.parse(start));
        entry.setEndTime(end == null ? null : Instant.parse(end));
        return entry;
    }
}
