"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from "recharts"
import { Activity, Brain, Zap, Calendar, Edit2, Home, BarChart2, Settings, LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"

// Custom Tooltip Component
interface CustomTooltipProps {
  active?: boolean;
  payload?: { name: string; value: number; color: string }[];
  label?: string;
}

const CustomTooltip = ({ active, payload, label }: CustomTooltipProps) => {
  if (active && payload && payload.length) {
    // Filter duplicates - only show visible lines (those with uppercase 'name')
    const uniquePayload = payload.filter((item) => 
      ['Work', 'Stagnant', 'Rot'].includes(item.name)
    );

    return (
      <div className="bg-[#1F1414] border border-[#A90C0C] p-2 rounded shadow-md">
        <p className="text-white font-bold mb-1">{label}</p>
        {uniquePayload.map((entry, index) => (
          <p key={index} style={{ color: entry.color }}>
            {entry.name}: {entry.value}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

const timelineData = [
  { time: '8am', work: 45, stagnant: 15, rot: 0 },
  { time: '9am', work: 95, stagnant: 25, rot: 0 },
  { time: '10am', work: 125, stagnant: 25, rot: 30 },
  { time: '11am', work: 180, stagnant: 30, rot: 30 },
  { time: '12pm', work: 200, stagnant: 70, rot: 30 },
  { time: '1pm', work: 240, stagnant: 90, rot: 30 },
  { time: '2pm', work: 240, stagnant: 90, rot: 90 },
  { time: '3pm', work: 250, stagnant: 90, rot: 140 },
  { time: '4pm', work: 295, stagnant: 105, rot: 140 },
]

const distributionData = [
  { name: 'Work', value: 60, color: '#D86F19' }, // landing-gradient-start
  { name: 'Stagnant', value: 25, color: '#9ca3af' }, // landing-eyebrow
  { name: 'Rot', value: 15, color: '#A90C0C' }, // landing-gradient-mid
]

const recentSessions = [
  { id: 1, activity: 'Biology Study', duration: '45m', type: 'Work', time: '2 hours ago' },
  { id: 2, activity: 'TikTok Scroll', duration: '15m', type: 'Rot', time: '3 hours ago' },
  { id: 3, activity: 'Bus Commute', duration: '30m', type: 'Stagnant', time: '4 hours ago' },
  { id: 4, activity: 'Calculus Prep', duration: '60m', type: 'Work', time: '5 hours ago' },
]

export default function DashboardPage() {
  return (
    <div className="flex min-h-screen bg-landing-gradient-end text-white">
      {/* Sidebar */}
      <aside className="w-56 bg-black/20 border-r border-white/20 p-6 flex flex-col">
        <div className="text-3xl font-bold mb-10 text-landing-gradient-start">RotTrack</div>
        <nav className="space-y-4 flex-1 mt-10">
          <Button variant="ghost" className="w-full justify-start text-lg text-white hover:text-white hover:bg-white/10">
            <Home className="mr-3 h-5 w-5" />
            Dashboard
          </Button>
          <Button variant="ghost" className="w-full justify-start text-lg text-landing-eyebrow hover:text-white hover:bg-white/10">
            <BarChart2 className="mr-3 h-5 w-5" />
            Analytics
          </Button>
          <Button variant="ghost" className="w-full justify-start text-lg text-landing-eyebrow hover:text-white hover:bg-white/10">
            <Settings className="mr-3 h-5 w-5" />
            Settings
          </Button>
        </nav>
        <Button variant="ghost" className="w-full justify-start text-lg text-red-400 hover:text-red-300 hover:bg-red-900/10 mt-auto">
          <LogOut className="mr-3 h-5 w-5" />
          Log Out
        </Button>
      </aside>

      {/* Main Content */}
      <main className="flex-1 p-8 space-y-8 overflow-y-auto">
      <h1 className="text-4xl font-bold tracking-tight text-white mb-8">Dashboard</h1>

      {/* Row 1: The Scoreboard */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card className="bg-black/40 border-transparent text-white flex flex-col h-56">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <p className="text-3xl font-bold text-white">Productivity Score</p>
            <Activity className="h-5 w-5 text-landing-gradient-start" />
          </CardHeader>
          <CardContent className="flex-1 flex flex-col justify-end">
            <div className="text-2xl font-bold text-landing-gradient-start mb-1">72%</div>
            <p className="text-xs text-landing-eyebrow">+2.1% from yesterday</p>
          </CardContent>
        </Card>
        <Card className="bg-black/40 border-transparent text-white flex flex-col h-56">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <p className="text-3xl font-bold text-white">Focus Time</p>
            <Brain className="h-5 w-5 text-landing-gradient-start" />
          </CardHeader>
          <CardContent className="flex-1 flex flex-col justify-end">
            <div className="text-4xl font-bold text-landing-gradient-start mb-1">4h 12m</div>
            <p className="text-xs text-landing-eyebrow">Target: 6h</p>
          </CardContent>
        </Card>
        <Card className="bg-black/40 border-transparent text-white flex flex-col h-56">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <p className="text-3xl font-bold text-white">Rot Time</p>
            <Zap className="h-5 w-5 text-landing-gradient-start" />
          </CardHeader>
          <CardContent className="flex-1 flex flex-col justify-end">
            <div className="text-2xl font-bold text-red-500 mb-1">2h 05m</div>
            <p className="text-xs text-landing-eyebrow">15m less than avg</p>
          </CardContent>
        </Card>
        <Card className="bg-black/40 border-transparent text-white flex flex-col h-56">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <p className="text-3xl font-bold text-white">Current Streak</p>
            <Calendar className="h-5 w-5 text-landing-gradient-start" />
          </CardHeader>
          <CardContent className="flex-1 flex flex-col justify-end">
            <div className="text-2xl font-bold text-landing-gradient-start mb-1">5 Days</div>
            <p className="text-xs text-landing-eyebrow">Keep it up!</p>
          </CardContent>
        </Card>
      </div>

      {/* Row 2: The Narrative */}
      <div className="grid gap-4 md:grid-cols-3">
        {/* Timeline Chart */}
        <Card className="col-span-2 bg-black/40 border-transparent text-white">
          <CardHeader>
            <CardTitle className="text-4xl font-bold text-white">Timeline (Last 24h)</CardTitle>
          </CardHeader>
          <CardContent className="pl-2">
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={timelineData}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#333" />
                  <XAxis dataKey="time" stroke="#9ca3af" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="#9ca3af" fontSize={12} tickLine={false} axisLine={false} tickFormatter={(value) => `${value}m`} width={50} dx={-5} />
                  <Tooltip content={<CustomTooltip />} />
                  <Legend wrapperStyle={{ color: '#9ca3af' }} />
                  <Line 
                    type="monotone" 
                    dataKey="work" 
                    name="Work" 
                    stroke="#D86F19" 
                    strokeWidth={2} 
                    dot={{ r: 4, fill: "#D86F19" }} 
                    activeDot={{ r: 6 }} 
                  />
                  <Line 
                    type="monotone" 
                    dataKey="stagnant" 
                    name="Stagnant" 
                    stroke="#9ca3af" 
                    strokeWidth={2} 
                    dot={{ r: 4, fill: "#9ca3af" }} 
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

        {/* Distribution Chart */}
        <Card className="col-span-1 bg-black/40 border-transparent text-white">
          <CardHeader>
            <CardTitle className="text-4xl font-bold text-white">Distribution</CardTitle>
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
                    <Tooltip contentStyle={{ backgroundColor: '#1F1414', borderColor: '#A90C0C', color: '#fff' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="flex flex-col justify-center space-y-4 pr-4">
                 {distributionData.map((item, index) => (
                    <div key={index} className="flex items-center justify-between gap-4">
                       <div className="flex items-center gap-2">
                          <div className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }} />
                          <span className="text-sm text-white">{item.name}</span>
                       </div>
                       <span className="text-sm font-bold text-landing-eyebrow">{item.value}%</span>
                    </div>
                 ))}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Row 3: The Action */}
      <Card className="bg-black/40 border-transparent text-white">
        <CardHeader>
          <CardTitle className="text-4xl font-bold text-white">Recent Sessions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {recentSessions.map((session) => (
              <div key={session.id} className="flex items-center justify-between border-b border-landing-gradient-mid/20 pb-4 last:border-0 last:pb-0">
                <div className="flex items-center gap-4">
                  <div className={`w-2 h-2 rounded-full ${
                    session.type === 'Work' ? 'bg-landing-gradient-start' : 
                    session.type === 'Rot' ? 'bg-landing-gradient-mid' : 'bg-landing-eyebrow'
                  }`} />
                  <div>
                    <p className="font-medium text-white">{session.activity}</p>
                    <p className="text-sm text-landing-eyebrow">{session.duration} • {session.time}</p>
                  </div>
                </div>
                <Button variant="ghost" size="sm" className="text-landing-eyebrow hover:text-white hover:bg-white/10">
                  <Edit2 className="h-4 w-4 mr-2" />
                  Edit
                </Button>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
      </main>
    </div>
  )
}