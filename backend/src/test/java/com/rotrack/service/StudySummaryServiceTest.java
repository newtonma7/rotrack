package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.repository.StudySummaryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudySummaryServiceTest {
    private final StudySummaryRepository repository = mock(StudySummaryRepository.class);
    private final StudySummaryService service = new StudySummaryService(repository, Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void canonicalizesSocialHandlesAndRejectsInvalidNamesBeforeReadingActivity() {
        when(repository.project(any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), " ALICE_1 "))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository).project(any(), eq("alice_1"), any());
        clearInvocations(repository);
        for (String name : new String[]{null, "ab", "a%lice", "a".repeat(25)}) {
            assertThatThrownBy(() -> service.get(UUID.randomUUID(), name))
                    .isInstanceOf(com.rotrack.exception.ValidationException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void countsOnlyRecentDaysAndStreakCanEndYesterday() {
        var today = java.time.LocalDate.parse("2026-06-11");
        var projection = java.util.stream.IntStream.rangeClosed(0, 365).mapToObj(offset -> {
            var day = today.minusDays(365L - offset);
            long seconds = day.equals(today.minusDays(7)) ? 10 : day.equals(today.minusDays(2)) ? 5
                    : day.equals(today.minusDays(1)) ? 1 : 0;
            return new StudySummaryRepository.DayTotal(day, seconds);
        }).toList();
        when(repository.project(any(), any(), any())).thenReturn(projection);
        var result = service.get(UUID.randomUUID(), "alice");
        assertThat(result.workSeconds()).isEqualTo(6);
        assertThat(result.studyDayStreak()).isEqualTo(2);
        assertThat(result.streakCapped()).isFalse();
        verify(repository).project(any(), eq("alice"), eq(Instant.parse("2026-06-11T12:00:00Z")));
    }

    @Test
    void yesterdayAnchorsStreakAndFullBoundedWindowIsCapped() {
        var today = java.time.LocalDate.parse("2026-06-11");
        when(repository.project(any(), any(), any())).thenReturn(java.util.stream.IntStream.rangeClosed(0, 365)
                .mapToObj(offset -> new StudySummaryRepository.DayTotal(today.minusDays(365L - offset), offset == 365 ? 0 : 1))
                .toList());
        var result = service.get(UUID.randomUUID(), "alice");
        assertThat(result.studyDayStreak()).isEqualTo(365);
        assertThat(result.streakCapped()).isTrue();

        when(repository.project(any(), any(), any())).thenReturn(java.util.stream.IntStream.rangeClosed(0, 365)
                .mapToObj(offset -> new StudySummaryRepository.DayTotal(today.minusDays(365L - offset),
                        offset == 365 || offset == 364 || offset == 362 ? 1 : 0)).toList());
        result = service.get(UUID.randomUUID(), "alice");
        assertThat(result.studyDayStreak()).isEqualTo(2); // today's streak stops at yesterday's gap
        assertThat(result.streakCapped()).isFalse();
    }

    @Test
    void zeroIsAvailableAndEmptyProjectionIsUnavailable() {
        when(repository.project(any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), "alice")).isInstanceOf(ResourceNotFoundException.class);
    }
}
