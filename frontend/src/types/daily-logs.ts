import type { RichTextDocument } from "@/types/notes";

export interface DailyLogRange {
  start: string;
  end: string;
  timeZone: string;
}
export interface DailyLogSummary {
  localDate: string;
  workSeconds: number;
  rotSeconds: number;
  sessionCount: number;
  noteCount: number;
  hasReflectionContent: boolean;
}
export interface DailyLogSummaryPage {
  range: DailyLogRange;
  days: DailyLogSummary[];
}
export interface DailySession {
  id: string;
  activityType: "WORK" | "ROT";
  segmentStartTime: string;
  segmentEndTime: string;
  durationSeconds: number;
  sessionLabel: string | null;
}
export interface NoteReference {
  id: string;
  title: string | null;
  preview: string;
  timeEntryId: string;
}
export interface Reflection {
  contentJson: RichTextDocument;
  contentText: string;
  contentSchemaVersion: 1;
  version: number;
  timeZoneAtCreation: string;
  updatedAt: string;
}
export interface DailyLog {
  localDate: string;
  timeZone: string;
  generated: {
    workSeconds: number;
    rotSeconds: number;
    sessions: DailySession[];
    noteReferences: NoteReference[];
  };
  reflection: Reflection | null;
}
export interface UpdateReflectionRequest {
  contentJson: RichTextDocument;
  expectedVersion: number;
}
export type DailyLogRangeQuery = DailyLogRange;
