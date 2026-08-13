package com.rotrack.dto;

import java.util.List;

public record HistoryPageDTO(
        List<HistoryTimeEntryDTO> entries,
        String nextCursor
) {
}
