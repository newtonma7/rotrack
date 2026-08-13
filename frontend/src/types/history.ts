/** Completed, owned time-entry history contract. The API never exposes active entries here. */
import type { ActivityType } from "@/types/time-entry";

export interface HistoryEntry {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string;
  durationSeconds: number;
  notes: string | null;
  attachedNoteCount: number;
}

export interface HistoryEntryInput {
  activityType: ActivityType;
  startTime: string;
  endTime: string;
  notes: string | null;
}

export interface HistoryPage {
  entries: HistoryEntry[];
  nextCursor: string | null;
}
