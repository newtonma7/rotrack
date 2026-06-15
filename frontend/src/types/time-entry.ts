export type ActivityType = "ROT" | "WORK";

export interface TimeEntry {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string | null;
  durationMinutes: number | null;
  notes: string | null;
}

export interface TimelinePoint {
  time: string;
  work: number;
  rot: number;
}

export interface RecentSession {
  id: string;
  activity: string;
  duration: string;
  type: ActivityType;
  time: string;
}

export interface DashboardStats {
  totalMinutes: Record<ActivityType, number>;
  timeline: TimelinePoint[];
  recentSessions: RecentSession[];
  productivityScore: number;
}

export interface ApiResponse<T> {
  data: T;
  message: string;
  timestamp: string;
}
