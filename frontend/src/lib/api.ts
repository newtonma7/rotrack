/**
 * HTTP client for the rotrack Spring Boot API.
 *
 * Data flow: Browser → fetch() → Spring Boot API → Supabase Postgres
 * Auth flow:  Supabase session JWT → Authorization: Bearer header → Spring validates via JWKS
 *
 * Supabase handles login; this file handles everything *after* login that needs our own backend.
 */

import { supabase } from "@/lib/supabase/client";
import { parseApiErrorResponse } from "@/lib/api-errors";
import type {
  ActivityType,
  ApiResponse,
  DashboardStats,
  DashboardStatsQuery,
  TimeEntry,
} from "@/types/time-entry";
import type { UserPreferences } from "@/types/preferences";
import type { HistoryEntry, HistoryEntryInput, HistoryPage } from "@/types/history";
import type {
  CreateNoteRequest,
  Note,
  NoteListFilters,
  NotePage,
  UpdateNoteRequest,
} from "@/types/notes";
import type { DailyLog, DailyLogRangeQuery, DailyLogSummaryPage, UpdateReflectionRequest } from "@/types/daily-logs";

import type { BlockPage, Relationship, RelationshipPage, RequestDirection, UserSearchPage } from "@/types/friendships";
import type { StudySummary } from "@/types/study-summary";
import type { StudyPresence } from "@/types/study-presence";
import { notifyStudyPresenceChanged } from "@/lib/study-presence-events";

// NEXT_PUBLIC_* vars are embedded at build time and visible in the browser — safe for URLs, not secrets.
const API_BASE = process.env.NEXT_PUBLIC_API_URL ||
  (process.env.NODE_ENV !== "production" ? "http://localhost:8080/api/v1" : "");

/**
 * Reads a valid Supabase access token for Spring Boot Bearer auth.
 *
 * The Spring API validates every JWT; reading the local Supabase session avoids a remote Auth
 * round-trip on each autosave. refreshSession() obtains a new token only near expiry.
 */
async function getAuthToken(): Promise<string | null> {
  const { data: { session } } = await supabase.auth.getSession();
  if (!session) return null;

  const expiresAt = session.expires_at ?? 0;
  const now = Math.floor(Date.now() / 1000);
  if (expiresAt > now + 60) {
    return session.access_token;
  }

  const { data: { session: refreshed }, error: refreshError } =
    await supabase.auth.refreshSession();
  if (refreshError || !refreshed?.access_token) {
    return session.access_token;
  }
  return refreshed.access_token;
}

/**
 * Central fetch wrapper — attaches JWT, parses the `{ data, message, timestamp }` envelope,
 * and throws on HTTP errors so callers can show a single error string in the UI.
 */
async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = await getAuthToken();
  if (!token) {
    throw new Error("Not authenticated");
  }

  if (!API_BASE) {
    throw new Error("API URL is not configured");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(options.headers ?? {}),
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = parseApiErrorResponse(body, response.status);
    const retryAfter = response.headers.get("Retry-After");
    if (retryAfter) {
      const seconds = Number(retryAfter);
      error.retryAfterMs = Number.isFinite(seconds) ? Math.max(0, seconds * 1000) : null;
    }
    throw error;
  }

  if (response.status === 204) return undefined as T;
  const json = (await response.json()) as ApiResponse<T>;
  return json.data;
}

export async function searchUsers(query: string): Promise<UserSearchPage> {
  return apiFetch<UserSearchPage>(`/users/search?${new URLSearchParams({ q: query })}`);
}
export async function getFriends(cursor?: string): Promise<RelationshipPage> {
  return apiFetch<RelationshipPage>(`/friends${cursor === undefined ? "" : `?${new URLSearchParams({ cursor })}`}`);
}
export async function getFriendRequests(direction: RequestDirection, cursor?: string): Promise<RelationshipPage> {
  const query = new URLSearchParams({ direction });
  if (cursor !== undefined) query.set("cursor", cursor);
  return apiFetch<RelationshipPage>(`/friend-requests?${query}`);
}
export async function getStudySummary(username: string): Promise<StudySummary> {
  return apiFetch<StudySummary>(`/social/summaries/${encodeURIComponent(username)}`);
}
export async function getStudyPresence(username: string, signal?: AbortSignal): Promise<StudyPresence> {
  return apiFetch<StudyPresence>(`/social/presence/${encodeURIComponent(username)}`, { signal, cache: "no-store" });
}
export async function getBlocks(cursor?: string): Promise<BlockPage> {
  return apiFetch<BlockPage>(`/blocks${cursor === undefined ? "" : `?${new URLSearchParams({ cursor })}`}`);
}
export async function requestFriend(username: string): Promise<Relationship> {
  return apiFetch<Relationship>("/friend-requests", { method: "POST", body: JSON.stringify({ username }) });
}
export async function acceptFriendRequest(id: string): Promise<Relationship> {
  return apiFetch<Relationship>(`/friend-requests/${encodeURIComponent(id)}/accept`, { method: "POST" });
}
export async function deleteFriendRequest(id: string): Promise<void> {
  await apiFetch<null>(`/friend-requests/${encodeURIComponent(id)}`, { method: "DELETE" });
}
export async function removeFriend(id: string): Promise<void> {
  await apiFetch<null>(`/friends/${encodeURIComponent(id)}`, { method: "DELETE" });
}
export async function blockUser(username: string): Promise<void> {
  await apiFetch<null>(`/blocks/${encodeURIComponent(username)}`, { method: "PUT" });
}
export async function unblockUser(username: string): Promise<void> {
  await apiFetch<null>(`/blocks/${encodeURIComponent(username)}`, { method: "DELETE" });
}

/** Start a new active session — backend rejects if another session is already open. */
export async function startSession(
  activityType: ActivityType,
  notes?: string
): Promise<TimeEntry> {
  const entry = await apiFetch<TimeEntry>("/time-entries/start", {
    method: "POST",
    body: JSON.stringify({ activityType, notes }),
  });
  notifyStudyPresenceChanged();
  return entry;
}

/** Stop a specific session by id — backend sets end_time; duration is derived from timestamps. */
export async function stopSession(entryId: string): Promise<TimeEntry> {
  const entry = await apiFetch<TimeEntry>(`/time-entries/${entryId}/stop`, {
    method: "PUT",
  });
  notifyStudyPresenceChanged();
  return entry;
}

/** Returns the user's open session, or null if none — used to restore UI on page load. */
export async function getActiveSession(): Promise<TimeEntry | null> {
  return apiFetch<TimeEntry | null>("/time-entries/active");
}

/** Timestamp-derived dashboard aggregates in the user's IANA timezone. */
export async function getDashboardStats(
  query: DashboardStatsQuery,
): Promise<DashboardStats> {
  const search = new URLSearchParams({ timeZone: query.timeZone });
  if (query.start) search.set("start", query.start);
  if (query.end) search.set("end", query.end);
  return apiFetch<DashboardStats>(`/dashboard/stats?${search.toString()}`);
}

/** Fetch the authenticated user's owned preferences. */
export async function getPreferences(): Promise<UserPreferences> {
  return apiFetch<UserPreferences>("/preferences");
}

/** Persist the complete preferences document; the API validates nullable values and bounds. */
export async function updatePreferences(preferences: UserPreferences): Promise<UserPreferences> {
  return apiFetch<UserPreferences>("/preferences", {
    method: "PUT",
    body: JSON.stringify(preferences),
  });
}

/** Completed-only history; cursors are returned by the API and passed back unchanged. */
export async function getHistory(cursor?: string): Promise<HistoryPage> {
  const search = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
  return apiFetch<HistoryPage>(`/time-entries/history${search}`);
}

export async function createHistoryEntry(entry: HistoryEntryInput): Promise<HistoryEntry> {
  return apiFetch<HistoryEntry>("/time-entries", {
    method: "POST",
    body: JSON.stringify(entry),
  });
}

export async function updateHistoryEntry(id: string, entry: HistoryEntryInput): Promise<HistoryEntry> {
  return apiFetch<HistoryEntry>(`/time-entries/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(entry),
  });
}

export async function deleteHistoryEntry(id: string): Promise<void> {
  await apiFetch<null>(`/time-entries/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

/** List owned Note summaries; filters are opaque API values and combine with AND. */
export async function getNotes(filters: NoteListFilters = {}): Promise<NotePage> {
  const search = new URLSearchParams();
  if (filters.cursor !== undefined) search.set("cursor", filters.cursor);
  if (filters.attachment !== undefined) search.set("attachment", filters.attachment);
  if (filters.timeEntryId !== undefined) search.set("timeEntryId", filters.timeEntryId);
  const query = search.toString();
  return apiFetch<NotePage>(`/notes${query ? `?${query}` : ""}`);
}

export async function createNote(request: CreateNoteRequest, idempotencyKey: string): Promise<Note> {
  return apiFetch<Note>("/notes", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(request),
  });
}

export async function getNote(id: string): Promise<Note> {
  return apiFetch<Note>(`/notes/${encodeURIComponent(id)}`);
}

export async function updateNote(id: string, request: UpdateNoteRequest): Promise<Note> {
  return apiFetch<Note>(`/notes/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

/** Fetches a complete local-date range of generated, owner-private daily summaries. */
export async function getDailyLogs(query: DailyLogRangeQuery): Promise<DailyLogSummaryPage> {
  const search = new URLSearchParams({ start: query.start, end: query.end, timeZone: query.timeZone });
  return apiFetch<DailyLogSummaryPage>(`/daily-logs?${search.toString()}`);
}

export async function getDailyLog(localDate: string, timeZone: string): Promise<DailyLog> {
  const search = new URLSearchParams({ timeZone });
  return apiFetch<DailyLog>(`/daily-logs/${encodeURIComponent(localDate)}?${search.toString()}`);
}

/** Idempotency key is required for first creation (expectedVersion 0), never for updates. */
export async function updateReflection(
  localDate: string,
  request: UpdateReflectionRequest,
  idempotencyKey: string | undefined,
  timeZone: string,
): Promise<import("@/types/daily-logs").Reflection> {
  const headers: Record<string, string> = idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {};
  const search = new URLSearchParams({ timeZone });
  return apiFetch<import("@/types/daily-logs").Reflection>(`/daily-logs/${encodeURIComponent(localDate)}/reflection?${search.toString()}`, {
    method: "PUT",
    headers,
    body: JSON.stringify(request),
  });
}

export async function deleteNote(id: string, expectedVersion: number): Promise<void> {
  const search = new URLSearchParams({ expectedVersion: String(expectedVersion) });
  await apiFetch<null>(`/notes/${encodeURIComponent(id)}?${search.toString()}`, {
    method: "DELETE",
  });
}
