package com.rotrack.dto;

import java.util.List;

public record HistoryPageDTO(
        List<TimeEntryDTO> entries,
        String nextCursor
) {
}
