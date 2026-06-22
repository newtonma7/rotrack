/**
 * Shared TypeScript shapes for the rotrack frontend ↔ Spring Boot API contract.
 *
 * Layer: frontend types only (no runtime code).
 * These interfaces mirror JSON returned by the backend DTOs in `backend/.../dto/`.
 * Keeping them here gives autocomplete and catches mismatches at compile time.
 */

/** Two-bucket domain model — must match Postgres enum `activity_type` and Java `ActivityType`. */
export type ActivityType = "ROT" | "WORK";

/** A single timed session row from `time_entries` (active if `endTime` is null). */
export interface TimeEntry {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string | null;
  durationMinutes: number | null;
  notes: string | null;
}

/** One point on the dashboard line chart (minutes per bucket for a time label). */
export interface TimelinePoint {
  time: string;
  work: number;
  rot: number;
}

/** Row in the "Recent sessions" list on the dashboard. */
export interface RecentSession {
  id: string;
  activity: string;
  duration: string;
  type: ActivityType;
  time: string;
}

/** Weekly aggregates from `GET /dashboard/stats` — feeds Recharts on `/dashboard`. */
export interface DashboardStats {
  totalMinutes: Record<ActivityType, number>;
  timeline: TimelinePoint[];
  recentSessions: RecentSession[];
  productivityScore: number;
}

/** Standard wrapper shape from Spring Boot controllers (`ApiResponse.java`). */
export interface ApiResponse<T> {
  data: T;
  message: string;
  timestamp: string;
}
