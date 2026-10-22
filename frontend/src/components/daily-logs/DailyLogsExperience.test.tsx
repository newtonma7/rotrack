// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DailyLogsExperience } from "@/components/daily-logs/DailyLogsExperience";
import { getDailyLog, getDailyLogs, getPreferences } from "@/lib/api";
import type { DailyLog } from "@/types/daily-logs";

const push = vi.fn();
const replace = vi.fn();
let pathName = "/daily-logs";
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, replace }), usePathname: () => pathName }));
vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a> }));
vi.mock("@/components/notes/RichTextEditor", () => ({ RichTextEditor: () => <div contentEditable aria-label="Reflection content" /> }));
vi.mock("@/lib/api", () => ({ getDailyLog: vi.fn(), getDailyLogs: vi.fn(), getPreferences: vi.fn() }));

const detail = (localDate: string): DailyLog => ({ localDate, timeZone: "UTC", generated: { workSeconds: 0, rotSeconds: 0, sessions: [], noteReferences: [] }, reflection: null });
const summaries = { range: { start: "2025-12-28", end: "2026-02-08", timeZone: "UTC" }, days: [] };

beforeEach(() => {
  cleanup();
  vi.clearAllMocks();
  pathName = "/daily-logs";
  vi.useFakeTimers({ toFake: ["Date"] }); vi.setSystemTime(new Date("2026-01-15T12:00:00Z"));
  vi.mocked(getPreferences).mockResolvedValue({ timeZone: "UTC", dailyWorkGoalMinutes: null, shareStudySummary: false, shareActiveStudyStatus: false });
  vi.mocked(getDailyLogs).mockResolvedValue(summaries);
  vi.mocked(getDailyLog).mockImplementation(async (date) => detail(date));
});

afterEach(() => vi.useRealTimers());

describe("daily log calendar selection", () => {
  it("uses the saved timezone to choose today's date on first load", async () => {
    vi.setSystemTime(new Date("2026-01-01T01:00:00Z"));
    vi.mocked(getPreferences).mockResolvedValue({ timeZone: "America/Los_Angeles", dailyWorkGoalMinutes: null, shareStudySummary: false, shareActiveStudyStatus: false });
    render(<DailyLogsExperience />);
    await waitFor(() => expect(getDailyLog).toHaveBeenLastCalledWith("2025-12-31", "America/Los_Angeles"));
  });

  it("honors an explicit URL timezone over saved preferences", async () => {
    vi.setSystemTime(new Date("2026-01-01T01:00:00Z"));
    render(<DailyLogsExperience initialTimeZone="America/Los_Angeles" />);
    await waitFor(() => expect(getDailyLog).toHaveBeenLastCalledWith("2025-12-31", "America/Los_Angeles"));
    fireEvent.change(screen.getByRole("combobox", { name: "Timezone" }), { target: { value: "Asia/Tokyo" } });
    expect(replace).toHaveBeenCalledWith("/daily-logs?timeZone=Asia%2FTokyo", { scroll: false });
  });

  it("advances from January to February by one calendar month", async () => {
    render(<DailyLogsExperience />);
    await screen.findByRole("heading", { name: "January 2026" });
    fireEvent.click(screen.getAllByRole("button", { name: "Next month" })[0]);
    await screen.findByRole("heading", { name: "February 2026" });
  });

  it("does not display the previous timezone's projection while a new query is pending", async () => {
    pathName = "/daily-logs/2026-01-15";
    render(<DailyLogsExperience localDate="2026-01-15" />);
    await screen.findByRole("heading", { name: "January 15, 2026" });
    vi.mocked(getDailyLog).mockImplementation(() => new Promise(() => {}));
    fireEvent.change(screen.getByRole("combobox", { name: "Timezone" }), { target: { value: "Asia/Tokyo" } });
    expect(screen.queryByRole("heading", { name: "January 15, 2026" })).toBeNull();
    expect(screen.getByText("Loading daily logs…")).toBeTruthy();
  });

  it("retains the date-owned Reflection editor through a timezone load and failure/retry", async () => {
    pathName = "/daily-logs/2026-01-15";
    render(<DailyLogsExperience localDate="2026-01-15" />);
    await screen.findByRole("heading", { name: "January 15, 2026" });
    const editor = screen.getByLabelText("Reflection content");
    vi.mocked(getDailyLog).mockRejectedValueOnce(new Error("unavailable"));
    fireEvent.change(screen.getByRole("combobox", { name: "Timezone" }), { target: { value: "Asia/Tokyo" } });
    await screen.findByRole("alert");
    expect(editor.isConnected).toBe(true);
    expect(screen.queryByRole("heading", { name: "January 15, 2026" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(screen.getByText("Loading daily logs…")).toBeTruthy();
    await screen.findByRole("heading", { name: "January 15, 2026" });
    expect(screen.getByLabelText("Reflection content")).toBe(editor);
    expect(getDailyLog).toHaveBeenLastCalledWith("2026-01-15", "Asia/Tokyo");
  });

  it("ignores a stale date response that resolves after the current selection", async () => {
    pathName = "/daily-logs/2026-08-01";
    const pending = new Map<string, (value: DailyLog) => void>();
    vi.mocked(getDailyLog).mockImplementation((date) => new Promise((resolve) => pending.set(date, resolve)));
    vi.mocked(getDailyLogs).mockResolvedValue(summaries);
    const { rerender } = render(<DailyLogsExperience localDate="2026-08-01" />);
    await act(async () => { await Promise.resolve(); });
    pathName = "/daily-logs/2026-08-02";
    rerender(<DailyLogsExperience localDate="2026-08-02" />);
    await act(async () => { await Promise.resolve(); });
    await waitFor(() => expect(pending.has("2026-08-02")).toBe(true));
    await act(async () => { pending.get("2026-08-02")?.(detail("2026-08-02")); await Promise.resolve(); });
    await act(async () => { pending.get("2026-08-01")?.(detail("2026-08-01")); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByRole("heading", { name: "August 2, 2026" })).toBeTruthy());
    expect(screen.queryByRole("heading", { name: "August 1, 2026" })).toBeNull();
  });
});
