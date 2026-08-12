"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { History as HistoryIcon, Settings2, Timer } from "lucide-react";
import { PreferencesForm } from "@/components/settings/PreferencesForm";
import { SignOutButton } from "@/components/auth/SignOutButton";
import { Button } from "@/components/ui/button";
import { getPreferences } from "@/lib/api";
import type { UserPreferences } from "@/types/preferences";

export default function SettingsPage() {
  const [preferences, setPreferences] = useState<UserPreferences | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadPreferences = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPreferences(await getPreferences());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Preferences could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPreferences();
  }, [loadPreferences]);

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-4 px-6 py-4 md:px-10">
          <Link href="/" className="font-display text-2xl tracking-[-0.02em]">
            rotrack<span className="text-[var(--rt-orange)]">.</span>
          </Link>
          <nav aria-label="Application" className="flex w-full flex-wrap items-center justify-start gap-2 sm:w-auto sm:justify-end">
            <Button variant="ghost" asChild className="rounded-full">
              <Link href="/dashboard">dashboard</Link>
            </Button>
            <Button variant="ghost" asChild className="rounded-full">
              <Link href="/tracker"><Timer aria-hidden="true" />tracker</Link>
            </Button>
            <Button variant="ghost" asChild className="rounded-full">
              <Link href="/history"><HistoryIcon aria-hidden="true" />history</Link>
            </Button>
            <span aria-current="page" className="flex items-center gap-2 rounded-full bg-[var(--rt-cream-soft)] px-4 py-2 text-sm font-semibold">
              <Settings2 aria-hidden="true" className="size-4" />settings
            </span>
            <SignOutButton className="rounded-full text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)]" />
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-[1000px] px-6 py-12 md:px-10 md:py-16">
        <div className="mb-10 max-w-3xl">
          <p className="mb-3 text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">your defaults</p>
          <h1 className="font-display text-[clamp(2.8rem,7vw,5.5rem)] leading-[0.92] tracking-[-0.02em]">settings that stay yours<span className="text-[var(--rt-orange)]">.</span></h1>
          <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[var(--rt-ink-muted)]">Choose how rotrack understands your local day. Sharing stays private until you turn it on.</p>
        </div>

        {loading && !preferences ? (
          <div role="status" aria-live="polite" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-10">
            <p className="text-lg text-[var(--rt-ink-muted)]">Loading preferences…</p>
          </div>
        ) : error ? (
          <div role="alert" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8 md:p-10">
            <p className="font-display text-3xl">settings stayed put.</p>
            <p className="mt-3 max-w-xl text-[var(--rt-ink-muted)]">{error}</p>
            <Button onClick={() => void loadPreferences()} className="mt-6 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] hover:bg-[var(--rt-orange-deep)]">Try again</Button>
          </div>
        ) : preferences ? (
          <PreferencesForm preferences={preferences} />
        ) : null}
      </main>
    </div>
  );
}
