"use client"

/**
 * Dashboard page — read-only analytics view for the signed-in user.
 *
 * Data flow: mount → getDashboardStats() → Spring aggregates time_entries → Recharts
 * Auth: protected by dashboard/layout.tsx (redirects to /signin if no Supabase session)
 *
 * EMPTY_STATS is the fallback when the API is down or the user has no sessions yet —
 * charts still render instead of crashing.
 */

import Link from "next/link"
import { useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from "recharts"
import { Activity, Brain, Zap, Calendar, Edit2, Home, BarChart2, Settings, LogOut, Timer } from "lucide-react"
import { Button } from "@/components/ui/button"
import { supabase } from "@/lib/supabase/client"
import { useRouter } from "next/navigation"
import { getDashboardStats } from "@/lib/api"
import type { DashboardStats } from "@/types/time-entry"

interface CustomTooltipProps {
  active?: boolean;
  payload?: { name: string; value: number; color: string }[];
  label?: string;
}

const CustomTooltip = ({ active, payload, label }: CustomTooltipProps) => {
  if (active && payload && payload.length) {
    const uniquePayload = payload.filter((item) =>
      ["Work", "Rot"].includes(item.name)
    );

    return (
      <div className="bg-[var(--rt-paper)] border border-[var(--rt-line)] p-2 rounded-lg shadow-md">
        <p className="text-[var(--rt-ink)] font-bold mb-1">{label}</p>
        {uniquePayload.map((entry, index) => (
          <p key={index} className="text-[var(--rt-ink-muted)]">
            {entry.name}: {entry.value}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

/** Converts backend minute totals to human-readable "4h 12m" for score cards. */
function formatMinutes(total: number): string {
  const hours = Math.floor(total / 60)
  const minutes = total % 60
  if (hours === 0) return `${minutes}m`
  return `${hours}h ${minutes}m`
}

const EMPTY_STATS: DashboardStats = {
  totalMinutes: { ROT: 0, WORK: 0 },
  timeline: [
    { time: "8am", work: 0, rot: 0 },
    { time: "9am", work: 0, rot: 0 },
    { time: "10am", work: 0, rot: 0 },
    { time: "11am", work: 0, rot: 0 },
    { time: "12pm", work: 0, rot: 0 },
    { time: "1pm", work: 0, rot: 0 },
    { time: "2pm", work: 0, rot: 0 },
    { time: "3pm", work: 0, rot: 0 },
    { time: "4pm", work: 0, rot: 0 },
  ],
  recentSessions: [],
  productivityScore: 0,
}

export default function DashboardPage() {
  const router = useRouter()
  const [stats, setStats] = useState<DashboardStats>(EMPTY_STATS)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Single fetch on mount — Phase 5 may replace this with React Query for caching/refetch.
  useEffect(() => {
    void (async () => {
      try {
        const data = await getDashboardStats()
        setStats(data)
        setError(null)
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard")
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const handleLogout = async () => {
    await supabase.auth.signOut()
    router.push("/")
  }

  const workMinutes = stats.totalMinutes.WORK ?? 0
  const rotMinutes = stats.totalMinutes.ROT ?? 0
  const totalMinutes = workMinutes + rotMinutes

  const distributionData = totalMinutes === 0
    ? [
        { name: "Work", value: 0, color: "var(--rt-orange)" },
        { name: "Rot", value: 0, color: "#A90C0C" },
      ]
    : [
        { name: "Work", value: Math.round((workMinutes * 100) / totalMinutes), color: "var(--rt-orange)" },
        { name: "Rot", value: Math.round((rotMinutes * 100) / totalMinutes), color: "#A90C0C" },
      ]

  return (
    <div className="flex min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
      <aside className="w-56 bg-[var(--rt-paper)] border-r border-[var(--rt-line)] p-6 flex flex-col">
        <Link href="/" className="font-display text-2xl mb-10 text-[var(--rt-ink)] hover:text-[var(--rt-orange)] transition-colors">
          rotrack
        </Link>
        <nav className="space-y-2 flex-1 mt-6">
          <Button variant="ghost" className="w-full justify-start text-base text-[var(--rt-ink)] hover:text-[var(--rt-orange)] hover:bg-[var(--rt-cream-soft)]">
            <Home className="mr-3 h-5 w-5" />
            Dashboard
          </Button>
          <Button variant="ghost" asChild className="w-full justify-start text-base text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange)] hover:bg-[var(--rt-cream-soft)]">
            <Link href="/tracker">
              <Timer className="mr-3 h-5 w-5" />
              Tracker
            </Link>
          </Button>
          <Button variant="ghost" className="w-full justify-start text-base text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange)] hover:bg-[var(--rt-cream-soft)]">
            <BarChart2 className="mr-3 h-5 w-5" />
            Analytics
          </Button>
          <Button variant="ghost" className="w-full justify-start text-base text-[var(--rt-ink-muted)] hover:text-[var(--rt-orange)] hover:bg-[var(--rt-cream-soft)]">
            <Settings className="mr-3 h-5 w-5" />
            Settings
          </Button>
        </nav>
        <Button
          variant="ghost"
          onClick={handleLogout}
          className="w-full justify-start text-base text-[var(--rt-ink-muted)] hover:text-red-600 hover:bg-red-50 mt-auto"
        >
          <LogOut className="mr-3 h-5 w-5" />
          Log Out
        </Button>
      </aside>

      <main className="flex-1 p-8 space-y-8 overflow-y-auto">
        <h1 className="font-display text-4xl tracking-tight text-[var(--rt-ink)] mb-8">Dashboard</h1>

        {loading && (
          <p className="text-[var(--rt-ink-muted)]">Loading stats...</p>
        )}
        {error && (
          <p className="text-[var(--rt-ink-soft)] text-sm">
            {error}. Showing empty data until the API is available.
          </p>
        )}

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card className="bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)] flex flex-col h-56">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <p className="text-lg font-semibold">Productivity Score</p>
              <Activity className="h-5 w-5 text-[var(--rt-orange)]" />
            </CardHeader>
            <CardContent className="flex-1 flex flex-col justify-end">
              <div className="text-4xl font-bold text-[var(--rt-orange)] mb-1 tabular-nums">{stats.productivityScore}%</div>
              <p className="text-xs text-[var(--rt-ink-muted)]">Last 7 days</p>
            </CardContent>
          </Card>
          <Card className="bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)] flex flex-col h-56">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <p className="text-lg font-semibold">Focus Time</p>
              <Brain className="h-5 w-5 text-[var(--rt-orange)]" />
            </CardHeader>
            <CardContent className="flex-1 flex flex-col justify-end">
              <div className="text-4xl font-bold text-[var(--rt-orange)] mb-1 tabular-nums">{formatMinutes(workMinutes)}</div>
              <p className="text-xs text-[var(--rt-ink-muted)]">Work bucket</p>
            </CardContent>
          </Card>
          <Card className="bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)] flex flex-col h-56">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <p className="text-lg font-semibold">Rot Time</p>
              <Zap className="h-5 w-5 text-[#A90C0C]" />
            </CardHeader>
            <CardContent className="flex-1 flex flex-col justify-end">
              <div className="text-4xl font-bold text-[#A90C0C] mb-1 tabular-nums">{formatMinutes(rotMinutes)}</div>
              <p className="text-xs text-[var(--rt-ink-muted)]">Rot bucket</p>
            </CardContent>
          </Card>
          <Card className="bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)] flex flex-col h-56">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <p className="text-lg font-semibold">Total Tracked</p>
              <Calendar className="h-5 w-5 text-[var(--rt-orange)]" />
            </CardHeader>
            <CardContent className="flex-1 flex flex-col justify-end">
              <div className="text-4xl font-bold text-[var(--rt-orange)] mb-1 tabular-nums">{formatMinutes(totalMinutes)}</div>
              <p className="text-xs text-[var(--rt-ink-muted)]">Last 7 days</p>
            </CardContent>
          </Card>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <Card className="col-span-2 bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)]">
            <CardHeader>
              <CardTitle className="text-2xl font-display">Timeline (Last 7 days)</CardTitle>
            </CardHeader>
            <CardContent className="pl-2">
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={stats.timeline}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--rt-line)" />
                    <XAxis dataKey="time" stroke="var(--rt-ink-muted)" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis stroke="var(--rt-ink-muted)" fontSize={12} tickLine={false} axisLine={false} tickFormatter={(value) => `${value}m`} width={50} dx={-5} />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend wrapperStyle={{ color: "var(--rt-ink-muted)" }} />
                    <Line
                      type="monotone"
                      dataKey="work"
                      name="Work"
                      stroke="var(--rt-orange)"
                      strokeWidth={2}
                      dot={{ r: 4, fill: "var(--rt-orange)" }}
                      activeDot={{ r: 6 }}
                    />
                    <Line
                      type="monotone"
                      dataKey="rot"
                      name="Rot"
                      stroke="#A90C0C"
                      strokeWidth={2}
                      dot={{ r: 4, fill: "#A90C0C" }}
                      activeDot={{ r: 6 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <Card className="col-span-1 bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)]">
            <CardHeader>
              <CardTitle className="text-2xl font-display">Distribution</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-col md:flex-row h-[300px]">
                <div className="flex-1 min-w-[200px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={distributionData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={80}
                        paddingAngle={5}
                        dataKey="value"
                        stroke="none"
                      >
                        {distributionData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={{ backgroundColor: "var(--rt-paper)", color: "var(--rt-ink)", borderRadius: "8px", border: "1px solid var(--rt-line)" }} itemStyle={{ color: "var(--rt-ink)" }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex flex-col justify-center space-y-4 pr-4">
                  {distributionData.map((item, index) => (
                    <div key={index} className="flex items-center justify-between gap-4">
                      <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }} />
                        <span className="text-sm">{item.name}</span>
                      </div>
                      <span className="text-sm font-bold text-[var(--rt-ink-muted)] tabular-nums">{item.value}%</span>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <Card className="bg-[var(--rt-paper)] border-[var(--rt-line)] text-[var(--rt-ink)]">
          <CardHeader>
            <CardTitle className="text-2xl font-display">Recent Sessions</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {stats.recentSessions.length === 0 ? (
                <p className="text-[var(--rt-ink-muted)] text-sm">
                  No sessions yet.{" "}
                  <Link href="/tracker" className="text-[var(--rt-orange)] hover:underline">
                    Start tracking
                  </Link>
                </p>
              ) : (
                stats.recentSessions.map((session) => (
                  <div key={session.id} className="flex items-center justify-between border-b border-[var(--rt-line)] pb-4 last:border-0 last:pb-0">
                    <div className="flex items-center gap-4">
                      <div className={`w-2 h-2 rounded-full ${
                        session.type === "WORK" ? "bg-[var(--rt-orange)]" : "bg-[#A90C0C]"
                      }`} />
                      <div>
                        <p className="font-medium">{session.activity}</p>
                        <p className="text-sm text-[var(--rt-ink-muted)]">{session.duration} • {session.time}</p>
                      </div>
                    </div>
                    <Button variant="ghost" size="sm" className="text-[var(--rt-ink-muted)] hover:text-[var(--rt-ink)] hover:bg-[var(--rt-cream-soft)]">
                      <Edit2 className="h-4 w-4 mr-2" />
                      Edit
                    </Button>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
