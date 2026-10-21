package com.rotrack.dto;

import com.rotrack.model.ActivityType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DailyLogDTO {
    private DailyLogDTO() {
    }

    public record Range(LocalDate start, LocalDate end, String timeZone) {
    }

    public record Summary(
            LocalDate localDate,
            long workSeconds,
            long rotSeconds,
            long sessionCount,
            long noteCount,
            boolean hasReflectionContent
    ) {
    }

    public record SummaryPage(Range range, List<Summary> days) {
    }

    public record Session(
            String id,
            ActivityType activityType,
            Instant segmentStartTime,
            Instant segmentEndTime,
            long durationSeconds,
            String sessionLabel
    ) {
    }

    public record NoteReference(String id, String title, String preview, String timeEntryId) {
    }

    public record Generated(long workSeconds, long rotSeconds, List<Session> sessions, List<NoteReference> noteReferences) {
    }

    public record Detail(LocalDate localDate, String timeZone, Generated generated, ReflectionDTO reflection) {
    }
}
