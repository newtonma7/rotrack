"use client";

import { useEffect, useRef } from "react";
import { stopActiveSessionBeacon } from "@/lib/api";

export function usePageVisibilityStop(activeEntryId: string | null) {
  const entryIdRef = useRef(activeEntryId);

  useEffect(() => {
    entryIdRef.current = activeEntryId;
  }, [activeEntryId]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden" && entryIdRef.current) {
        void stopActiveSessionBeacon(entryIdRef.current);
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);
}
