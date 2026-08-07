"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarDays, Gauge, LogOut, Timer, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getDashboardStats } from "@/lib/api";
import { supabase } from "@/lib/supabase/client";
import type { ActivityType, DashboardStats, TimeEntry } from "@/types/time-entry";
import { useRouter } from "next/navigation";

function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  if (seconds < 60) return `${seconds}s`;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours === 0) return `${minutes}m`;
  return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`;
}

function formatAxisDuration(totalSeconds: number): string {
  if (totalSeconds === 0) return "0";
  if (totalSeconds < 3600) return `${Math.round(totalSeconds / 60)}m`;
  const hours = totalSeconds / 3600;
  return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}h`;
}

function formatLocalDate(localDate: string, format: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(undefined, { ...format, timeZone: "UTC" }).format(
    new Date(`${localDate}T00:00:00Z`),
  );
}

function formatSessionDate(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone,
  }).format(new Date(instant));
}

export default function DashboardPage() {
  const router = useRouter();
  const timeZone = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    [],
  );
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Calendar days only make sense in the user's zone; the API converts these
      // boundaries to authoritative instants and handles DST before aggregating.
      setStats(await getDashboardStats({ timeZone }));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [timeZone]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  const handleLogout = async () => {
    await supabase.auth.signOut();
    router.push("/");
  };

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-4 px-6 py-4 md:px-10">
          <Link href="/" className="font-display text-2xl tracking-[-0.02em]">
            rotrack<span className="text-[var(--rt-orange)]">.</span>
          </Link>
          <nav aria-label="Application" className="flex items-center gap-2">
            <span className="rounded-full bg-[var(--rt-cream-soft)] px-4 py-2 text-sm font-semibold">
              dashboard
            </span>
            <Button variant="ghost" asChild className="rounded-full">
              <Link href="/tracker">
                <Timer aria-hidden="true" />
                tracker
              </Link>
            </Button>
            <Button
              variant="ghost"
              onClick={handleLogout}
              aria-label="log out"
              className="rounded-full text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)]"
            >
              <LogOut aria-hidden="true" />
              <span className="hidden sm:inline">log out</span>
            </Button>
          </nav>
        </div>
      </header>

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
        <MetricCard
          label="work"
          value={formatDuration(workSeconds)}
          detail="intentional focus"
          icon={<Zap aria-hidden="true" />}
          accent
        />
        <MetricCard
          label="rot"
          value={formatDuration(rotSeconds)}
          detail="explicitly tracked"
          icon={<Timer aria-hidden="true" />}
        />
        <MetricCard
          label="tracked"
          value={formatDuration(totalSeconds)}
          detail={`${stats.daily.length} local days`}
          icon={<CalendarDays aria-hidden="true" />}
        />
        <MetricCard
          label="work share"
          value={`${stats.productivityScore}%`}
          detail="work ÷ all tracked time"
          icon={<Gauge aria-hidden="true" />}
          inverted
        />
      </section>

      {isEmpty && (
        <section className="relative overflow-hidden rounded-[36px] bg-[var(--rt-ink)] p-8 text-[var(--rt-cream)] md:p-12">
          <div className="absolute -right-12 -top-16 h-48 w-48 rounded-full bg-[var(--rt-orange)]" aria-hidden="true" />
          <div className="relative max-w-xl">
            <p className="text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange-soft)]">clean slate</p>
            <h2 className="mt-3 font-display text-4xl leading-none">nothing tracked yet.</h2>
            <p className="mt-4 text-[var(--rt-cream)]/70">
              Idle time stays untracked. Start Work or Rot when you want a minute to count.
            </p>
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

function MetricCard({
  label,
  value,
  detail,
  icon,
  accent = false,
  inverted = false,
}: {
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

function DailyChart({ stats }: { stats: DashboardStats }) {
  return (
    <Card className="rounded-[32px] border-[var(--rt-line)] bg-[var(--rt-paper)]">
      <CardHeader className="flex flex-row items-end justify-between gap-4">
        <div>
          <p className="text-[0.8rem] font-semibold uppercase tracking-[0.18em] text-[var(--rt-orange)]">daily split</p>
          <CardTitle className="mt-2 font-display text-3xl">where each day landed.</CardTitle>
        </div>
        <p className="hidden text-right text-xs text-[var(--rt-ink-muted)] sm:block">
          {stats.range.timeZone}
        </p>
      </CardHeader>
      <CardContent>
        <div
          role="img"
          aria-label="Daily Work and Rot tracked seconds"
          className="h-72 rounded-[24px] bg-[var(--rt-cream)] px-1 pb-2 pt-6 sm:px-4"
        >
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={stats.daily} barGap={4} margin={{ top: 8, right: 8, left: -14, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="var(--rt-line)" strokeDasharray="3 5" />
              <XAxis
                dataKey="localDate"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "var(--rt-ink-muted)", fontSize: 11, fontWeight: 600 }}
                tickFormatter={(date: string) => formatLocalDate(date, { weekday: "short" }).toUpperCase()}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                width={48}
                tick={{ fill: "var(--rt-ink-muted)", fontSize: 10 }}
                tickFormatter={formatAxisDuration}
              />
              <Tooltip content={<DailyTooltip />} cursor={{ fill: "var(--rt-cream-soft)" }} />
              <Bar dataKey="workSeconds" name="Work" fill="var(--rt-orange)" radius={[10, 10, 0, 0]} isAnimationActive={false} />
              <Bar dataKey="rotSeconds" name="Rot" fill="var(--rt-ink-soft)" radius={[10, 10, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <table aria-label="Daily tracked seconds" className="sr-only">
          <caption>Daily Work and Rot tracked seconds</caption>
          <thead>
            <tr>
              <th scope="col">Local date</th>
              <th scope="col">Work seconds</th>
              <th scope="col">Rot seconds</th>
            </tr>
          </thead>
          <tbody>
            {stats.daily.map((day) => (
              <tr key={day.localDate}>
                <th scope="row">{day.localDate}</th>
                <td>{day.workSeconds}</td>
                <td>{day.rotSeconds}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="mt-4 flex gap-5 text-xs text-[var(--rt-ink-muted)]">
          <LegendDot type="WORK" />
          <LegendDot type="ROT" />
        </div>
      </CardContent>
    </Card>
  );
}

function DailyTooltip({ active, payload, label }: {
  active?: boolean;
  payload?: Array<{ name?: string; value?: number; color?: string }>;
  label?: string;
}) {
  if (!active || !payload?.length || !label) return null;
  return (
    <div className="rounded-xl border border-[var(--rt-line)] bg-[var(--rt-paper)] p-3 shadow-lg">
      <p className="mb-2 text-xs font-semibold uppercase tracking-[0.12em] text-[var(--rt-ink-muted)]">
        {formatLocalDate(label, { month: "short", day: "numeric" })}
      </p>
      {payload.map((item) => (
        <p key={item.name} className="text-sm" style={{ color: item.color }}>
          {item.name}: {formatDuration(item.value ?? 0)}
        </p>
      ))}
    </div>
  );
}

function LegendDot({ type }: { type: ActivityType }) {
  return (
    <span className="flex items-center gap-2">
      <span className={`h-2.5 w-2.5 rounded-full ${type === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} />
      {type === "WORK" ? "Work" : "Rot"}
    </span>
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
      <div className="mb-2 flex justify-between text-sm">
        <span>{label}</span>
        <span className="tabular-nums text-[var(--rt-ink-muted)]">{duration} · {share}%</span>
      </div>
      <div
        role="progressbar"
        aria-label={`${label} share`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={share}
        className="h-2 overflow-hidden rounded-full bg-[var(--rt-cream-soft)]"
      >
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
                  <span className={`h-3 w-3 shrink-0 rounded-full ${session.activityType === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} />
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
