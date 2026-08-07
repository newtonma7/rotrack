"use client";

/**
 * Main tracker UI — the core product surface on `/tracker`.
 *
 * Two-bucket rule: only Work and Rot buttons. Both buckets are explicit; idle time is untracked.
 * This component is presentational; all timer/API logic lives in useTimeTracking().
 *
 * UX rules enforced here:
 * - Only one bucket active at a time (other button disabled while session runs)
 * - Stop button visible only when a session is open
 * - Figtree on elapsed time (matches app UI; tabular-nums keeps digits from jumping)
 */

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useTimeTracking } from "@/hooks/useTimeTracking";
export function ActiveTracker() {
  const { activeEntry, elapsed, loading, error, start, stop } = useTimeTracking();

  const isActive = Boolean(activeEntry);
  const activeType = activeEntry?.activityType;

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <Card className="border-[var(--rt-line)] bg-[var(--rt-paper)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)]">
        <CardHeader>
          <CardTitle className="font-heading text-3xl">Active session</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="text-center py-8">
            <p className="text-[0.8rem] uppercase tracking-widest text-[var(--rt-ink-muted)] mb-3">
              {isActive ? `tracking ${activeType?.toLowerCase()}` : "no active session"}
            </p>
            <div
              className="font-sans font-bold text-[clamp(3rem,10vw,5rem)] leading-none tabular-nums tracking-[0.08em] text-[var(--rt-orange)]"
            >
              {elapsed}
            </div>
          </div>

          {/* Starting a bucket calls the API immediately — no separate "confirm" step. */}
          <div className="grid grid-cols-2 gap-4">
            <ActivityButton
              label="Work"
              description="Intentional focus"
              active={activeType === "WORK"}
              disabled={loading || (isActive && activeType !== "WORK")}
              onClick={() => start("WORK")}
              variant="work"
            />
            <ActivityButton
              label="Rot"
              description="Explicitly tracked time"
              active={activeType === "ROT"}
              disabled={loading || (isActive && activeType !== "ROT")}
              onClick={() => start("ROT")}
              variant="rot"
            />
          </div>

          {isActive && (
            <Button
              onClick={() => void stop()}
              disabled={loading}
              variant="outline"
              className="w-full rounded-full border-[var(--rt-ink)]"
            >
              Stop session
            </Button>
          )}

          {error && (
            <p className="text-sm text-center text-[var(--rt-ink-soft)]">{error}</p>
          )}
        </CardContent>
      </Card>

      <p className="text-center text-sm text-[var(--rt-ink-muted)]">
        Sessions keep running across tabs, navigation, reloads, and browser closure until you stop them explicitly.{" "}
        <Link href="/dashboard" className="text-[var(--rt-orange)] hover:underline">
          View dashboard
        </Link>
      </p>
    </div>
  );
}

function ActivityButton({
  label,
  description,
  active,
  disabled,
  onClick,
  variant,
}: {
  label: string;
  description: string;
  active: boolean;
  disabled: boolean;
  onClick: () => void;
  variant: "work" | "rot";
}) {
  const isWork = variant === "work";

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={[
        "rounded-[28px] border p-6 text-left transition-all hover:-translate-y-0.5 disabled:opacity-50 disabled:hover:translate-y-0",
        active
          ? isWork
            ? "bg-[var(--rt-ink)] text-[var(--rt-cream)] border-[var(--rt-ink)]"
            : "bg-[var(--rt-ink-soft)] text-[var(--rt-cream)] border-[var(--rt-ink-soft)]"
          : "bg-[var(--rt-cream)] border-[var(--rt-line)] text-[var(--rt-ink)] hover:border-[var(--rt-orange)]",
      ].join(" ")}
    >
      <span className="font-heading text-2xl block">{label}</span>
      <span className={[
        "text-sm mt-1 block",
        active ? "opacity-80" : "text-[var(--rt-ink-muted)]",
      ].join(" ")}>
        {description}
      </span>
    </button>
  );
}
