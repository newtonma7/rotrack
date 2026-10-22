package com.rotrack.dto;

/** Exhaustive public allowlist for a peer's study summary. */
public record StudySummaryDTO(long workSeconds, int studyDayStreak, boolean streakCapped) {}
