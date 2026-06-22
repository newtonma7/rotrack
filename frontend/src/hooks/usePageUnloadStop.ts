"use client";

/**
 * Auto-stop hook — rotrack product rule: closing this tab stops the active timer.
 *
 * Layer: frontend hook (browser APIs only).
 * Listens: window "pagehide" → calls stopActiveSessionBeacon() when the page unloads.
 * Used by: useTimeTracking (tracker page).
 *
 * Why pagehide instead of visibilitychange?
 * - Tab switch → visibilitychange fires ("hidden") but pagehide does NOT → session keeps running.
 * - Tab close / full navigation away → pagehide fires → session stops via keepalive fetch.
 *
 * Why a ref? The listener is registered once; the ref always holds the latest entry id
 * without re-attaching the event listener on every render.
 */

import { useEffect, useRef } from "react";
import { stopActiveSessionBeacon } from "@/lib/api";

export function usePageUnloadStop(activeEntryId: string | null) {
  const entryIdRef = useRef(activeEntryId);

  // Keep ref in sync whenever the active session changes (start/stop).
  useEffect(() => {
    entryIdRef.current = activeEntryId;
  }, [activeEntryId]);

  useEffect(() => {
    const handlePageHide = () => {
      if (entryIdRef.current) {
        void stopActiveSessionBeacon(entryIdRef.current);
      }
    };

    window.addEventListener("pagehide", handlePageHide);
    return () => {
      window.removeEventListener("pagehide", handlePageHide);
    };
  }, []);
}
