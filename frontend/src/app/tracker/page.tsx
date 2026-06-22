"use client";

/**
 * Tracker route shell — thin page wrapper around ActiveTracker.
 *
 * Auth is enforced in tracker/layout.tsx (not here).
 * This file only provides nav chrome; timer logic is in components/tracker/ActiveTracker.tsx.
 */

import Link from "next/link";
import { ActiveTracker } from "@/components/tracker/ActiveTracker";
import { SignOutButton } from "@/components/auth/SignOutButton";

export default function TrackerPage() {
  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]">
        <div className="mx-auto max-w-4xl px-6 py-5 flex items-center justify-between">
          <Link href="/" className="font-display text-2xl hover:text-[var(--rt-orange)] transition-colors">
            rotrack
          </Link>
          <div className="flex items-center gap-4">
            <Link
              href="/dashboard"
              className="text-sm text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange)] transition-colors"
            >
              Dashboard
            </Link>
            <SignOutButton className="text-sm text-[var(--rt-ink-muted)] hover:text-red-600 px-2 h-auto" />
          </div>
        </div>
      </header>
      <main className="px-6 py-16">
        <ActiveTracker />
      </main>
    </div>
  );
}
