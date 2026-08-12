/** Owned profile preferences returned by `/api/v1/preferences`. */
export interface UserPreferences {
  timeZone: string | null;
  dailyWorkGoalMinutes: number | null;
  shareStudySummary: boolean;
  shareActiveStudyStatus: boolean;
}
