package com.rotrack.service;

import com.rotrack.dto.DashboardStatsDTO;
import com.rotrack.dto.RecentSessionDTO;
import com.rotrack.dto.TimelinePointDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.model.ActivityType;
import com.rotrack.model.TimeEntry;
import com.rotrack.repository.TimeEntryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
                    throw new IllegalStateException("An active session already exists");
                });

        TimeEntry entry = new TimeEntry();
        entry.setUserId(userId);
        entry.setActivityType(activityType);
        entry.setStartTime(Instant.now());
        entry.setNotes(notes);
        return toDto(timeEntryRepository.save(entry));
    }

    @Transactional
    public TimeEntryDTO stopSession(UUID userId, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Time entry not found"));

        if (entry.getEndTime() != null) {
            throw new IllegalStateException("Session is already stopped");
        }

        Instant endTime = Instant.now();
        entry.setEndTime(endTime);
        return toDto(timeEntryRepository.save(entry));
    }

    @Transactional
    public TimeEntryDTO stopActiveSession(UUID userId) {
        TimeEntry entry = timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .orElseThrow(() -> new IllegalStateException("No active session found"));
        return stopSession(userId, entry.getId());
    }

    public TimeEntryDTO getActiveSession(UUID userId) {
        return timeEntryRepository.findFirstByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
                .map(this::toDto)
                .orElse(null);
    }

    public DashboardStatsDTO getWeeklyStats(UUID userId) {
        Instant end = Instant.now();
        Instant start = end.minus(7, ChronoUnit.DAYS);

        Map<ActivityType, Integer> totals = new EnumMap<>(ActivityType.class);
        totals.put(ActivityType.WORK, 0);
        totals.put(ActivityType.ROT, 0);

        for (Object[] row : timeEntryRepository.sumDurationByActivityType(userId, start, end)) {
            ActivityType type = row[0] instanceof ActivityType activityType
                    ? activityType
                    : ActivityType.valueOf(row[0].toString());
            Number minutes = (Number) row[1];
            totals.put(type, minutes.intValue());
        }

        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                userId, start, end);

        List<TimelinePointDTO> timeline = buildTimeline(entries);
        List<RecentSessionDTO> recent = buildRecentSessions(entries);

        int work = totals.get(ActivityType.WORK);
        int rot = totals.get(ActivityType.ROT);
        int total = work + rot;
        int productivityScore = total == 0 ? 0 : Math.round((work * 100f) / total);

        return new DashboardStatsDTO(totals, timeline, recent, productivityScore);
    }

    private List<TimelinePointDTO> buildTimeline(List<TimeEntry> entries) {
        Map<Integer, int[]> hourly = new java.util.TreeMap<>();
        for (int h = 8; h <= 16; h++) {
            hourly.put(h, new int[]{0, 0});
        }

        for (TimeEntry entry : entries) {
            Integer durationMinutes = durationMinutes(entry);
            if (durationMinutes == null) {
                continue;
            }
            int hour = entry.getStartTime().atZone(ZoneId.systemDefault()).getHour();
            if (!hourly.containsKey(hour)) {
                continue;
            }
            int[] bucket = hourly.get(hour);
            if (entry.getActivityType() == ActivityType.WORK) {
                bucket[0] += durationMinutes;
            } else {
                bucket[1] += durationMinutes;
            }
        }

        List<TimelinePointDTO> points = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : hourly.entrySet()) {
            String label = e.getKey() < 12 ? e.getKey() + "am" : (e.getKey() == 12 ? "12pm" : (e.getKey() - 12) + "pm");
            points.add(new TimelinePointDTO(label, e.getValue()[0], e.getValue()[1]));
        }
        return points;
    }

    private List<RecentSessionDTO> buildRecentSessions(List<TimeEntry> entries) {
        return entries.stream()
                .filter(e -> e.getEndTime() != null)
                .sorted((a, b) -> b.getEndTime().compareTo(a.getEndTime()))
                .limit(10)
                .map(entry -> {
                    String duration = durationMinutes(entry) + "m";
                    String label = entry.getNotes() != null && !entry.getNotes().isBlank()
                            ? entry.getNotes()
                            : entry.getActivityType().name().toLowerCase();
                    String timeAgo = formatRelative(entry.getEndTime());
                    return new RecentSessionDTO(
                            entry.getId().toString(),
                            label,
                            duration,
                            entry.getActivityType(),
                            timeAgo
                    );
                })
                .toList();
    }

    private String formatRelative(Instant instant) {
        long minutes = Duration.between(instant, Instant.now()).toMinutes();
        if (minutes < 60) {
            return minutes + " minutes ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hours ago";
        }
        return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"));
    }

    private Integer durationMinutes(TimeEntry entry) {
        if (entry.getEndTime() == null) {
            return null;
        }
        return Math.toIntExact(Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes());
    }

    private TimeEntryDTO toDto(TimeEntry entry) {
        return new TimeEntryDTO(
                entry.getId(),
                entry.getActivityType(),
                entry.getStartTime(),
                entry.getEndTime(),
                durationMinutes(entry),
                entry.getNotes()
        );
    }
}
