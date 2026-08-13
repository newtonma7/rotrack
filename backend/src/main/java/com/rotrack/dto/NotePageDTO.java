package com.rotrack.dto;

import java.util.List;

public record NotePageDTO(List<NoteSummaryDTO> notes, String nextCursor) {}
