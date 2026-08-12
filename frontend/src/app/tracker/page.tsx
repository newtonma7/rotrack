"use client";

/**
 * Tracker route shell — thin page wrapper around ActiveTracker.
 *
 * Auth is enforced in tracker/layout.tsx (not here).
 * This file only provides nav chrome; timer logic is in components/tracker/ActiveTracker.tsx.
 */

import { ApplicationHeader } from "@/components/app/ApplicationHeader";
import { ActiveTracker } from "@/components/tracker/ActiveTracker";

export default function TrackerPage() {
  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <ApplicationHeader />
      <main className="px-6 py-16">
        <h1 className="sr-only">Track your time</h1>
        <ActiveTracker />
      </main>
    </div>
  );
}
