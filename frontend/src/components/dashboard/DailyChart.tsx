"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatAxisDuration, formatDuration, formatLocalDate } from "@/lib/format";
import type { ActivityType, DashboardStats } from "@/types/time-entry";

export default function DailyChart({ stats }: { stats: DashboardStats }) {
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
        <div className="mt-4 flex gap-5 text-xs text-[var(--rt-ink-muted)]" aria-label="Chart legend">
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
      <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${type === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[var(--rt-ink-soft)]"}`} />
      {type === "WORK" ? "Work" : "Rot"}
    </span>
  );
}
