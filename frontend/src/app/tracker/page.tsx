"use client";

import Link from "next/link";
import { ActiveTracker } from "@/components/tracker/ActiveTracker";

export default function TrackerPage() {
  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]">
        <div className="mx-auto max-w-4xl px-6 py-5 flex items-center justify-between">
          <Link href="/" className="font-display text-2xl hover:text-[var(--rt-orange)] transition-colors">
            rotrack
          </Link>
          <Link
            href="/dashboard"
            className="text-sm text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange)] transition-colors"
          >
            Dashboard
          </Link>
        </div>
      </header>
      <main className="px-6 py-16">
        <ActiveTracker />
      </main>
    </div>
  );
}
