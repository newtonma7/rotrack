"use client";

/**
 * Tracker state hook — orchestrates active session UI + API calls.
 *
 * Data flow on mount:  GET /time-entries/active → restore timer if session exists
 * Data flow on start:  POST /time-entries/start → store entry, begin local tick
 * Data flow on stop:   PUT /time-entries/{id}/stop → clear entry
 *
 * The elapsed display is computed client-side for smooth 1s updates; authoritative
 * duration is only written when the backend stops the session.
 */

import { useCallback, useEffect, useState } from "react";
import {
  getActiveSession,
  startSession,
  stopSession,
} from "@/lib/api";
import { usePageUnloadStop } from "@/hooks/usePageUnloadStop";
import type { ActivityType, TimeEntry } from "@/types/time-entry";

/** Formats milliseconds since startTime as HH:MM:SS for the tracker display. */
function formatElapsed(startTime: string): string {
  const start = new Date(startTime).getTime();
  const now = Date.now();
  const totalSeconds = Math.max(0, Math.floor((now - start) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds]
    .map((n) => String(n).padStart(2, "0"))
    .join(":");
}

export function useTimeTracking() {
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null);
  const [elapsed, setElapsed] = useState("00:00:00");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Register auto-stop on tab close (not tab switch) whenever we have an open session id.
  usePageUnloadStop(activeEntry?.id ?? null);

  const refreshActive = useCallback(async () => {
    try {
      const session = await getActiveSession();
      setActiveEntry(session);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load session");
    } finally {
      setLoading(false);
    }
  }, []);

  // On first load, ask the backend if a session was left running (e.g. after refresh).
  useEffect(() => {
    void refreshActive();
  }, [refreshActive]);

  // Local 1-second ticker — purely cosmetic until stop; server owns the truth.
  useEffect(() => {
    if (!activeEntry?.startTime) {
      setElapsed("00:00:00");
      return;
    }

    const tick = () => setElapsed(formatElapsed(activeEntry.startTime));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [activeEntry?.startTime]);

  const handleStart = async (activityType: ActivityType) => {
    setLoading(true);
    setError(null);
    try {
      const entry = await startSession(activityType);
      setActiveEntry(entry);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start session");
    } finally {
      setLoading(false);
    }
  };

  const handleStop = async () => {
    if (!activeEntry) return;
    setLoading(true);
    setError(null);
    try {
      await stopSession(activeEntry.id);
      setActiveEntry(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to stop session");
    } finally {
      setLoading(false);
    }
  };

  return {
    activeEntry,
    elapsed,
    loading,
    error,
    start: handleStart,
    stop: handleStop,
    refresh: refreshActive,
  };
}
