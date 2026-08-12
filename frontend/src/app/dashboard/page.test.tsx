/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DashboardPage from "@/app/dashboard/page";
import { getDashboardStats, getPreferences } from "@/lib/api";
import type { DashboardStats } from "@/types/time-entry";

vi.mock("@/lib/api", () => ({ getDashboardStats: vi.fn(), getPreferences: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }), usePathname: () => "/dashboard" }));
vi.mock("@/lib/supabase/client", () => ({
  supabase: { auth: { signOut: vi.fn() } },
}));
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Bar: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const emptyStats: DashboardStats = {
  range: {
    start: "2026-08-01T04:00:00Z",
    end: "2026-08-08T04:00:00Z",
    timeZone: "America/New_York",
  },
  totalSeconds: { WORK: 0, ROT: 0 },
  daily: [
    { localDate: "2026-08-01", workSeconds: 0, rotSeconds: 0 },
    { localDate: "2026-08-02", workSeconds: 0, rotSeconds: 0 },
  ],
  recentSessions: [],
  productivityScore: 0,
};

const populatedStats: DashboardStats = {
  ...emptyStats,
  totalSeconds: { WORK: 23_400, ROT: 3_600 },
  daily: [
    { localDate: "2026-08-01", workSeconds: 18_000, rotSeconds: 1_800 },
    { localDate: "2026-08-02", workSeconds: 5_400, rotSeconds: 1_800 },
  ],
  recentSessions: [
    {
      id: "work-session",
      activityType: "WORK",
      startTime: "2026-08-07T14:30:00Z",
      endTime: "2026-08-07T16:00:00Z",
      durationSeconds: 5_400,
      notes: "dashboard semantics",
    },
    {
      id: "rot-session",
      activityType: "ROT",
      startTime: "2026-08-06T20:45:00Z",
      endTime: "2026-08-06T21:45:00Z",
      durationSeconds: 3_600,
      notes: null,
    },
  ],
  productivityScore: 87,
};

describe("DashboardPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getPreferences).mockResolvedValue({
      timeZone: null,
      dailyWorkGoalMinutes: null,
      shareStudySummary: false,
      shareActiveStudyStatus: false,
    });
  });

  it("shows a complete empty state after loading a range with no tracked time", async () => {
    vi.mocked(getDashboardStats).mockResolvedValue(emptyStats);

    render(<DashboardPage />);

    expect(screen.getByText(/loading your last seven days/i)).toBeTruthy();
    expect(await screen.findByText(/nothing tracked yet/i)).toBeTruthy();
    expect(screen.getByRole("link", { name: /start tracking/i }).getAttribute("href")).toBe("/tracker");
  });

  it("uses the saved timezone for dashboard requests", async () => {
    vi.mocked(getPreferences).mockResolvedValue({
      timeZone: "Europe/Berlin",
      dailyWorkGoalMinutes: 90,
      shareStudySummary: false,
      shareActiveStudyStatus: false,
    });
    vi.mocked(getDashboardStats).mockResolvedValue(emptyStats);

    render(<DashboardPage />);

    await waitFor(() => expect(getDashboardStats).toHaveBeenCalledWith({ timeZone: "Europe/Berlin" }));
  });

  it("falls back to the browser timezone when no timezone is saved", async () => {
    vi.mocked(getDashboardStats).mockResolvedValue(emptyStats);
    vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockReturnValue({ timeZone: "Asia/Tokyo" } as Intl.ResolvedDateTimeFormatOptions);

    render(<DashboardPage />);

    await waitFor(() => expect(getDashboardStats).toHaveBeenCalledWith({ timeZone: "Asia/Tokyo" }));
  });

  it("renders populated summary, distribution, and recent-session data", async () => {
    vi.mocked(getDashboardStats).mockResolvedValue(populatedStats);

    render(<DashboardPage />);

    expect(await screen.findByText("6h 30m")).toBeTruthy();
    expect(screen.getAllByText("1h")).toHaveLength(2);
    expect(screen.getByText("7h 30m")).toBeTruthy();
    expect(screen.getAllByText("87%")).toHaveLength(2);
    expect(screen.getByText("dashboard semantics")).toBeTruthy();
    expect(screen.getAllByText("rot")).toHaveLength(2);
    expect(screen.getByRole("table", { name: /daily tracked seconds/i })).toBeTruthy();
  });

  it("lets the user retry a failed dashboard request", async () => {
    vi.mocked(getDashboardStats)
      .mockRejectedValueOnce(new Error("Dashboard unavailable"))
      .mockResolvedValueOnce(emptyStats);

    render(<DashboardPage />);

    expect((await screen.findByRole("alert")).textContent).toContain("Dashboard unavailable");
    fireEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => expect(getDashboardStats).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/nothing tracked yet/i)).toBeTruthy();
  });
});
