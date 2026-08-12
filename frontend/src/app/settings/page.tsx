"use client";

import { useCallback, useEffect, useState } from "react";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";
import { PreferencesForm } from "@/components/settings/PreferencesForm";
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
      <ApplicationHeader />

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
