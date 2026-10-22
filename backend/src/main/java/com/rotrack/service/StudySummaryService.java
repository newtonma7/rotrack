package com.rotrack.service;

import com.rotrack.dto.StudySummaryDTO;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.repository.StudySummaryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudySummaryService {
    private final StudySummaryRepository repository;
    private final Clock clock;

    public StudySummaryService(StudySummaryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true, timeout = 5)
    public StudySummaryDTO get(UUID viewer, String username) {
        String canonical = UsernameValidator.canonicalize(username);
        Instant now = clock.instant();
        List<StudySummaryRepository.DayTotal> days = repository.project(viewer, canonical, now);
        if (days.isEmpty()) throw new ResourceNotFoundException("Study summary not found");

        Map<LocalDate, Long> secondsByDay = days.stream().collect(Collectors.toMap(
                StudySummaryRepository.DayTotal::day, StudySummaryRepository.DayTotal::seconds));
        // Rows are ordered by day and the final day is today in the peer's saved zone (UTC when unset).
        LocalDate today = days.getLast().day();
        long work = days.stream().filter(day -> !day.day().isBefore(today.minusDays(6)))
                .mapToLong(StudySummaryRepository.DayTotal::seconds).sum();
        LocalDate end = secondsByDay.getOrDefault(today, 0L) > 0 ? today : today.minusDays(1);
        int streak = 0;
        LocalDate current = end;
        while (streak < 366 && secondsByDay.getOrDefault(current, 0L) > 0) {
            streak++;
            current = current.minusDays(1);
        }
        boolean capped = streak > 0 && end.minusDays(streak - 1L).equals(today.minusDays(365));
        return new StudySummaryDTO(work, streak, capped);
    }

}
