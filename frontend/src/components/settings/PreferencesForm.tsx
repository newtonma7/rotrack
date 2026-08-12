"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getBrowserTimeZone, isValidTimeZone } from "@/lib/timezone";
import { updatePreferences } from "@/lib/api";
import type { UserPreferences } from "@/types/preferences";

type PreferencesFormProps = {
  preferences: UserPreferences;
};

type DraftPreferences = Omit<UserPreferences, "timeZone" | "dailyWorkGoalMinutes"> & {
  timeZone: string;
  dailyWorkGoalMinutes: string;
};

function toDraft(preferences: UserPreferences): DraftPreferences {
  return {
    timeZone: preferences.timeZone ?? "",
    dailyWorkGoalMinutes: preferences.dailyWorkGoalMinutes?.toString() ?? "",
    shareStudySummary: preferences.shareStudySummary,
    shareActiveStudyStatus: preferences.shareActiveStudyStatus,
  };
}

function toPreferences(draft: DraftPreferences): UserPreferences {
  const goal = draft.dailyWorkGoalMinutes.trim();
  return {
    timeZone: draft.timeZone.trim() || null,
    dailyWorkGoalMinutes: goal ? Number(goal) : null,
    shareStudySummary: draft.shareStudySummary,
    shareActiveStudyStatus: draft.shareActiveStudyStatus,
  };
}

export function PreferencesForm({ preferences }: PreferencesFormProps) {
  const [draft, setDraft] = useState(() => toDraft(preferences));
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "saved">("idle");
  const browserTimeZone = getBrowserTimeZone();

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const timeZone = draft.timeZone.trim();
    const goal = draft.dailyWorkGoalMinutes.trim();
    const parsedGoal = goal ? Number(goal) : 0;

    if (timeZone && !isValidTimeZone(timeZone)) {
      setSaveError("Enter a valid IANA timezone, such as America/New_York.");
      setSaveState("idle");
      return;
    }
    if (goal && (!Number.isInteger(parsedGoal) || parsedGoal < 1 || parsedGoal > 1440)) {
      setSaveError("Daily Work goal must be a whole number from 1 to 1440 minutes.");
      setSaveState("idle");
      return;
    }

    setSaveError(null);
    setSaveState("saving");
    try {
      const saved = await updatePreferences(toPreferences(draft));
      setDraft(toDraft(saved));
      setSaveState("saved");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Preferences could not be saved.");
      setSaveState("idle");
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event)} aria-busy={saveState === "saving"} className="space-y-6">
      <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
        <CardHeader>
          <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">calendar</p>
          <CardTitle className="mt-2 font-display text-3xl">make the day yours.</CardTitle>
          <p className="max-w-2xl text-[var(--rt-ink-muted)]">
            These settings shape future calendar boundaries. Stored session timestamps stay exactly as they are.
          </p>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="timezone">Saved timezone</Label>
            <Input
              id="timezone"
              value={draft.timeZone}
              onChange={(event) => setDraft((current) => ({ ...current, timeZone: event.target.value }))}
              placeholder={browserTimeZone}
              autoComplete="off"
              spellCheck={false}
              aria-describedby="timezone-help"
            />
            <p id="timezone-help" className="text-sm text-[var(--rt-ink-muted)]">
              Leave blank to use this browser timezone: <span className="font-semibold text-[var(--rt-ink)]">{browserTimeZone}</span>.
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="daily-work-goal">Daily Work goal (minutes)</Label>
            <Input
              id="daily-work-goal"
              type="number"
              min={1}
              max={1440}
              step={1}
              inputMode="numeric"
              value={draft.dailyWorkGoalMinutes}
              onChange={(event) => setDraft((current) => ({ ...current, dailyWorkGoalMinutes: event.target.value }))}
              placeholder="optional"
              aria-describedby="daily-work-goal-help"
            />
            <p id="daily-work-goal-help" className="text-sm text-[var(--rt-ink-muted)]">Optional. Choose a whole number between 1 and 1440.</p>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
        <CardHeader>
          <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">privacy</p>
          <CardTitle className="mt-2 font-display text-3xl">keep sharing on your terms.</CardTitle>
          <p className="max-w-2xl text-[var(--rt-ink-muted)]">Both switches start off. Future social views only use a setting you choose.</p>
        </CardHeader>
        <CardContent className="space-y-4">
          <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-[var(--rt-line)] p-4 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-[var(--rt-orange)]">
            <input
              type="checkbox"
              checked={draft.shareStudySummary}
              onChange={(event) => setDraft((current) => ({ ...current, shareStudySummary: event.target.checked }))}
              className="mt-1 size-4 accent-[var(--rt-orange)]"
            />
            <span>
              <span className="block font-semibold">Share study summary</span>
              <span className="mt-1 block text-sm text-[var(--rt-ink-muted)]">Allow opted-in Work totals and study-day streaks to appear in future friend views.</span>
            </span>
          </label>
          <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-[var(--rt-line)] p-4 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-[var(--rt-orange)]">
            <input
              type="checkbox"
              checked={draft.shareActiveStudyStatus}
              onChange={(event) => setDraft((current) => ({ ...current, shareActiveStudyStatus: event.target.checked }))}
              className="mt-1 size-4 accent-[var(--rt-orange)]"
            />
            <span>
              <span className="block font-semibold">Share active study status</span>
              <span className="mt-1 block text-sm text-[var(--rt-ink-muted)]">Allow a future friend view to show when you are actively studying Work.</span>
            </span>
          </label>
        </CardContent>
      </Card>

      {saveError && <p role="alert" className="rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)]/30 p-4 text-sm">{saveError}</p>}
      <div className="flex flex-wrap items-center gap-4">
        <Button
          type="submit"
          disabled={saveState === "saving"}
          aria-label={saveState === "saving" ? "saving preferences" : "save preferences"}
          className="rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)] hover:bg-[var(--rt-orange-deep)]"
        >
          {saveState === "saving" ? "Saving…" : "Save preferences"}
        </Button>
        <p aria-live="polite" className="text-sm text-[var(--rt-ink-muted)]">
          {saveState === "saved" ? "Preferences saved." : "Private by default."}
        </p>
      </div>
    </form>
  );
}
