"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarDays, Gauge, Timer, Zap } from "lucide-react";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getDashboardStats, getPreferences } from "@/lib/api";
import { formatDuration, formatSessionDate } from "@/lib/format";
import type { DashboardStats, TimeEntry } from "@/types/time-entry";
import { getBrowserTimeZone } from "@/lib/timezone";

const DailyChart = dynamic(() => import("@/components/dashboard/DailyChart"), {
  ssr: false,
  loading: () => (
    <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
      <CardHeader>
        <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">daily split</p>
        <CardTitle className="mt-2 font-display text-3xl">where each day landed.</CardTitle>
      </CardHeader>
      <CardContent>
        <div role="status" aria-live="polite" className="h-72 animate-pulse rounded-[24px] bg-[var(--rt-cream)]" aria-label="Loading daily chart" />
      </CardContent>
    </Card>
  ),
});

export default function DashboardPage() {
  const browserTimeZone = useMemo(() => getBrowserTimeZone(), []);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Calendar days only make sense in the user's saved zone; the API converts
      // these boundaries to authoritative instants and handles DST before aggregating.
      const preferences = await getPreferences();
      setStats(await getDashboardStats({ timeZone: preferences.timeZone || browserTimeZone }));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [browserTimeZone]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <ApplicationHeader />

      <main className="mx-auto max-w-[1400px] px-6 py-12 md:px-10 md:py-16">
        <div className="mb-10 max-w-3xl">
          <p className="mb-3 text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">
            your last seven local days
          </p>
          <h1 className="font-display text-[clamp(2.8rem,7vw,5.5rem)] leading-[0.92] tracking-[-0.02em]">
            an honest look at your time<span className="text-[var(--rt-orange)]">.</span>
          </h1>
        </div>

        {loading && !stats ? (
          <LoadingState />
        ) : error ? (
          <ErrorState message={error} onRetry={() => void loadStats()} />
        ) : stats ? (
          <DashboardContent stats={stats} />
        ) : null}
      </main>
    </div>
  );
}

function LoadingState() {
  return (
    <div className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-10" aria-live="polite">
      <div className="mb-5 h-3 w-24 animate-pulse rounded-full bg-[var(--rt-orange-soft)]" />
      <p className="text-lg text-[var(--rt-ink-muted)]">Loading your last seven days…</p>
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8 md:p-10">
      <p className="font-display text-3xl">the numbers didn&apos;t land.</p>
      <p className="mt-3 max-w-xl text-[var(--rt-ink-muted)]">{message}</p>
      <Button onClick={onRetry} className="mt-6 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] hover:bg-[var(--rt-orange-deep)]">
        Try again
      </Button>
    </div>
  );
}

function DashboardContent({ stats }: { stats: DashboardStats }) {
  const workSeconds = stats.totalSeconds.WORK ?? 0;
  const rotSeconds = stats.totalSeconds.ROT ?? 0;
  const totalSeconds = workSeconds + rotSeconds;
  const isEmpty = totalSeconds === 0;

  return (
    <div className="space-y-6">
      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="work" value={formatDuration(workSeconds)} detail="intentional focus" icon={<Zap aria-hidden="true" />} accent />
        <MetricCard label="rot" value={formatDuration(rotSeconds)} detail="explicitly tracked" icon={<Timer aria-hidden="true" />} />
        <MetricCard label="tracked" value={formatDuration(totalSeconds)} detail={`${stats.daily.length} local days`} icon={<CalendarDays aria-hidden="true" />} />
        <MetricCard label="work share" value={`${stats.productivityScore}%`} detail="work ÷ all tracked time" icon={<Gauge aria-hidden="true" />} inverted />
      </section>

      {isEmpty && (
        <section className="relative overflow-hidden rounded-[36px] bg-[var(--rt-ink)] p-8 text-[var(--rt-cream)] md:p-12">
          <div className="absolute -right-12 -top-16 h-48 w-48 rounded-full bg-[var(--rt-orange)]" aria-hidden="true" />
          <div className="relative max-w-xl">
            <p className="text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange-soft)]">clean slate</p>
            <h2 className="mt-3 font-display text-4xl leading-none">nothing tracked yet.</h2>
            <p className="mt-4 text-[var(--rt-cream)]/70">Idle time stays untracked. Start Work or Rot when you want a minute to count.</p>
            <Button asChild className="mt-7 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] hover:bg-[var(--rt-orange-deep)]">
              <Link href="/tracker">Start tracking</Link>
            </Button>
          </div>
        </section>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.7fr)_minmax(300px,0.8fr)]">
        <DailyChart stats={stats} />
        <DistributionCard workSeconds={workSeconds} rotSeconds={rotSeconds} score={stats.productivityScore} />
      </div>
      <RecentSessions sessions={stats.recentSessions} timeZone={stats.range.timeZone} />
    </div>
  );
}

function MetricCard({ label, value, detail, icon, accent = false, inverted = false }: {
  label: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
  accent?: boolean;
  inverted?: boolean;
}) {
  return (
    <Card className={`min-h-52 rounded-[28px] border ${inverted ? "border-[var(--rt-ink)] bg-[var(--rt-ink)] text-[var(--rt-cream)]" : "border-[var(--rt-line)] bg-[var(--rt-paper)]"}`}>
      <CardHeader className="flex flex-row items-center justify-between">
        <p className="text-sm font-semibold uppercase tracking-[0.14em] opacity-65">{label}</p>
        <span className={accent ? "text-[var(--rt-orange)]" : "opacity-65"}>{icon}</span>
      </CardHeader>
      <CardContent className="pt-5">
        <p className={`font-display text-5xl tabular-nums ${accent ? "text-[var(--rt-orange)]" : ""}`}>{value}</p>
        <p className="mt-2 text-sm opacity-60">{detail}</p>
      </CardContent>
    </Card>
  );
}

function DistributionCard({ workSeconds, rotSeconds, score }: { workSeconds: number; rotSeconds: number; score: number }) {
  const total = workSeconds + rotSeconds;
  const rotShare = total === 0 ? 0 : 100 - score;

  return (
    <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
      <CardHeader>
        <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">distribution</p>
        <CardTitle className="mt-2 font-display text-3xl">the whole picture.</CardTitle>
      </CardHeader>
      <CardContent className="space-y-8">
        <div className="flex aspect-square max-h-64 items-center justify-center rounded-full border-[28px] border-[var(--rt-cream-soft)]">
          <div className="text-center">
            <p className="font-display text-6xl tabular-nums text-[var(--rt-orange)]">{score}%</p>
            <p className="text-sm text-[var(--rt-ink-muted)]">work share</p>
          </div>
        </div>
        <div className="space-y-4">
          <ShareRow label="Work" share={score} duration={formatDuration(workSeconds)} accent />
          <ShareRow label="Rot" share={rotShare} duration={formatDuration(rotSeconds)} />
        </div>
      </CardContent>
    </Card>
  );
}

function ShareRow({ label, share, duration, accent = false }: { label: string; share: number; duration: string; accent?: boolean }) {
  return (
    <div>
      <div className="mb-2 flex justify-between text-sm"><span>{label}</span><span className="tabular-nums text-[var(--rt-ink-muted)]">{duration} · {share}%</span></div>
      <div role="progressbar" aria-label={`${label} share`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={share} className="h-2 overflow-hidden rounded-full bg-[var(--rt-cream-soft)]">
        <div className={`h-full rounded-full ${accent ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} style={{ width: `${share}%` }} />
      </div>
    </div>
  );
}

function RecentSessions({ sessions, timeZone }: { sessions: TimeEntry[]; timeZone: string }) {
  return (
    <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
      <CardHeader>
        <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">recent</p>
        <CardTitle className="mt-2 font-display text-3xl">completed sessions.</CardTitle>
      </CardHeader>
      <CardContent>
        {sessions.length === 0 ? (
          <p className="text-sm text-[var(--rt-ink-muted)]">Completed sessions in this range will appear here.</p>
        ) : (
          <ul className="divide-y divide-[var(--rt-line)]">
            {sessions.map((session) => (
              <li key={session.id} className="flex flex-wrap items-center justify-between gap-4 py-5 first:pt-0 last:pb-0">
                <div className="flex min-w-0 items-center gap-4">
                  <span aria-hidden="true" className={`h-3 w-3 shrink-0 rounded-full ${session.activityType === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} />
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{session.notes?.trim() || session.activityType.toLowerCase()}</p>
                    <p className="text-sm text-[var(--rt-ink-muted)]">{formatSessionDate(session.endTime!, timeZone)}</p>
                  </div>
                </div>
                <span className="font-display text-2xl tabular-nums">{formatDuration(session.durationSeconds ?? 0)}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
