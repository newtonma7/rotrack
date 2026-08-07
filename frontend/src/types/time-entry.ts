/**
 * Shared TypeScript shapes for the rotrack frontend ↔ Spring Boot API contract.
 * These interfaces mirror backend DTOs and keep timestamps as ISO-8601 strings.
 */

/** Two-bucket domain model — must match Postgres enum `activity_type` and Java `ActivityType`. */
export type ActivityType = "ROT" | "WORK";

/** A timed session (active when `endTime` and `durationSeconds` are null). */
export interface TimeEntry {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string | null;
  durationSeconds: number | null;
  notes: string | null;
}

export interface DashboardRange {
  start: string;
  end: string;
  timeZone: string;
}

export interface DailyStats {
  localDate: string;
  workSeconds: number;
  rotSeconds: number;
}

/** Timestamp-derived dashboard aggregates for one half-open local-date range. */
export interface DashboardStats {
  range: DashboardRange;
  totalSeconds: Record<ActivityType, number>;
  daily: DailyStats[];
  recentSessions: TimeEntry[];
  productivityScore: number;
}

export type DashboardStatsQuery = { timeZone: string } & (
  | { start?: never; end?: never }
  | { start: string; end: string }
);

/** Standard wrapper shape from Spring Boot controllers (`ApiResponse.java`). */
export interface ApiResponse<T> {
  data: T;
  message: string;
  timestamp: string;
}
