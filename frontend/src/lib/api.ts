/**
 * HTTP client for the rotrack Spring Boot API.
 *
 * Data flow: Browser → fetch() → Spring Boot (localhost:8080) → Supabase Postgres
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

// NEXT_PUBLIC_* vars are embedded at build time and visible in the browser — safe for URLs, not secrets.
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";

/**
 * Reads a valid Supabase access token for Spring Boot Bearer auth.
 *
 * getUser() hits Supabase to confirm the session is still valid (not just cached in localStorage).
 * refreshSession() issues a new access token when near expiry so the backend always gets a fresh JWT.
 */
async function getAuthToken(): Promise<string | null> {
  const { data: { user }, error: userError } = await supabase.auth.getUser();
  if (userError || !user) return null;

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
    throw parseApiErrorResponse(body, response.status);
  }

  const json = (await response.json()) as ApiResponse<T>;
  return json.data;
}

/** Start a new active session — backend rejects if another session is already open. */
export async function startSession(
  activityType: ActivityType,
  notes?: string
): Promise<TimeEntry> {
  return apiFetch<TimeEntry>("/time-entries/start", {
    method: "POST",
    body: JSON.stringify({ activityType, notes }),
  });
}

/** Stop a specific session by id — backend sets end_time; duration is derived from timestamps. */
export async function stopSession(entryId: string): Promise<TimeEntry> {
  return apiFetch<TimeEntry>(`/time-entries/${entryId}/stop`, {
    method: "PUT",
  });
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
