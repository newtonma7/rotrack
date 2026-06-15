import { supabase } from "@/lib/supabase/client";
import type {
  ActivityType,
  ApiResponse,
  DashboardStats,
  TimeEntry,
} from "@/types/time-entry";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";

async function getAuthToken(): Promise<string | null> {
  const { data } = await supabase.auth.getSession();
  return data.session?.access_token ?? null;
}

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
    throw new Error(body.error ?? `Request failed (${response.status})`);
  }

  const json = (await response.json()) as ApiResponse<T>;
  return json.data;
}

export async function startSession(
  activityType: ActivityType,
  notes?: string
): Promise<TimeEntry> {
  return apiFetch<TimeEntry>("/time-entries/start", {
    method: "POST",
    body: JSON.stringify({ activityType, notes }),
  });
}

export async function stopSession(entryId: string): Promise<TimeEntry> {
  return apiFetch<TimeEntry>(`/time-entries/${entryId}/stop`, {
    method: "PUT",
  });
}

export async function stopActiveSession(): Promise<TimeEntry> {
  return apiFetch<TimeEntry>("/time-entries/active/stop", {
    method: "PUT",
  });
}

export async function getActiveSession(): Promise<TimeEntry | null> {
  return apiFetch<TimeEntry | null>("/time-entries/active");
}

export async function getDashboardStats(): Promise<DashboardStats> {
  return apiFetch<DashboardStats>("/dashboard/stats");
}

export async function stopActiveSessionBeacon(entryId: string): Promise<void> {
  const token = await getAuthToken();
  if (!token) return;

  const url = `${API_BASE}/time-entries/${entryId}/stop`;

  await fetch(url, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}` },
    keepalive: true,
  });
}
