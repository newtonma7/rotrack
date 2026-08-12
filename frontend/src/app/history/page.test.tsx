/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import HistoryPage from "@/app/history/page";
import {
  createHistoryEntry,
  deleteHistoryEntry,
  getHistory,
  getPreferences,
  updateHistoryEntry,
} from "@/lib/api";
import { toDateTimeLocal, toIsoInstant } from "@/lib/datetime";
import { ApiRequestError } from "@/lib/api-errors";
import type { HistoryEntry } from "@/types/history";

vi.mock("@/lib/api", () => ({
  createHistoryEntry: vi.fn(),
  deleteHistoryEntry: vi.fn(),
  getHistory: vi.fn(),
  getPreferences: vi.fn(),
  updateHistoryEntry: vi.fn(),
}));
vi.mock("@/lib/supabase/client", () => ({ supabase: { auth: { signOut: vi.fn() } } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }), usePathname: () => "/history" }));

const entries: HistoryEntry[] = [{
  id: "entry-1",
  activityType: "WORK",
  startTime: "2026-08-12T10:00:00Z",
  endTime: "2026-08-12T11:00:00Z",
  durationSeconds: 61,
  notes: "deep work",
}];

const secondEntry: HistoryEntry = {
  id: "entry-2",
  activityType: "ROT",
  startTime: "2026-08-11T10:00:00Z",
  endTime: "2026-08-11T10:15:00Z",
  durationSeconds: 901,
  notes: null,
};

describe("HistoryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getHistory).mockResolvedValue({ entries, nextCursor: null });
    vi.mocked(getPreferences).mockResolvedValue({ timeZone: "America/New_York", dailyWorkGoalMinutes: null, shareStudySummary: false, shareActiveStudyStatus: false });
    vi.mocked(createHistoryEntry).mockResolvedValue(entries[0]);
    vi.mocked(updateHistoryEntry).mockResolvedValue(entries[0]);
    vi.mocked(deleteHistoryEntry).mockResolvedValue(undefined);
    vi.stubGlobal("confirm", vi.fn(() => true));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows loading, then completed entries in the API order", async () => {
    let resolve!: (value: { entries: HistoryEntry[]; nextCursor: null }) => void;
    vi.mocked(getHistory).mockReturnValue(new Promise((next) => { resolve = next; }));

    render(<HistoryPage />);
    expect(screen.getByRole("status").textContent).toMatch(/loading history/i);
    resolve({ entries, nextCursor: null });

    expect(await screen.findByText("deep work")).toBeTruthy();
    expect(screen.getByText(/work · 1m 1s/i)).toBeTruthy();
  });

  it("shows an empty state and retries a failed initial request", async () => {
    vi.mocked(getHistory)
      .mockRejectedValueOnce(new Error("History unavailable"))
      .mockResolvedValueOnce({ entries: [], nextCursor: null });

    render(<HistoryPage />);
    expect((await screen.findByRole("alert")).textContent).toContain("History unavailable");
    fireEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/no completed entries yet/i)).toBeTruthy();
  });

  it("validates and submits a new completed entry using the saved timezone", async () => {
    vi.mocked(getPreferences).mockResolvedValue({ timeZone: "Europe/Berlin", dailyWorkGoalMinutes: null, shareStudySummary: false, shareActiveStudyStatus: false });
    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /add entry/i }));
    fireEvent.click(screen.getByRole("button", { name: /save entry/i }));

    expect(await screen.findByText(/start time is required/i)).toBeTruthy();
    expect(screen.getByLabelText(/start time/i).getAttribute("aria-invalid")).toBe("true");

    fireEvent.click(screen.getByRole("combobox", { name: "Activity" }));
    fireEvent.click(screen.getByRole("option", { name: "Rot" }));
    fireEvent.change(screen.getByLabelText(/start time/i), { target: { value: "2026-08-12T14:30" } });
    fireEvent.change(screen.getByLabelText(/end time/i), { target: { value: "2026-08-12T14:45" } });
    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "a short reset" } });
    fireEvent.click(screen.getByRole("button", { name: /save entry/i }));

    await waitFor(() => expect(createHistoryEntry).toHaveBeenCalledWith({
      activityType: "ROT",
      startTime: toIsoInstant("2026-08-12T14:30", "Europe/Berlin"),
      endTime: toIsoInstant("2026-08-12T14:45", "Europe/Berlin"),
      notes: "a short reset",
    }));
    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(2));
  });

  it("edits an entry and requires confirmation before deleting it", async () => {
    render(<HistoryPage />);
    await screen.findByText("deep work");

    fireEvent.click(screen.getByRole("button", { name: /edit deep work/i }));
    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "updated note" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));
    await waitFor(() => expect(updateHistoryEntry).toHaveBeenCalledWith("entry-1", expect.objectContaining({ notes: "updated note" })));
    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(2));

    fireEvent.click(screen.getByRole("button", { name: /delete deep work/i }));
    expect(confirm).toHaveBeenCalled();
    await waitFor(() => expect(deleteHistoryEntry).toHaveBeenCalledWith("entry-1"));
    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(3));
  });

  it("shows saving and delete-error states", async () => {
    let resolveCreate!: (value: HistoryEntry) => void;
    vi.mocked(createHistoryEntry).mockReturnValue(new Promise((resolve) => { resolveCreate = resolve; }));
    vi.mocked(deleteHistoryEntry).mockRejectedValue(new Error("Delete unavailable"));

    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /add entry/i }));
    fireEvent.change(screen.getByLabelText(/start time/i), { target: { value: "2026-08-12T14:30" } });
    fireEvent.change(screen.getByLabelText(/end time/i), { target: { value: "2026-08-12T14:45" } });
    fireEvent.click(screen.getByRole("button", { name: /save entry/i }));
    expect((screen.getByRole("button", { name: /saving/i }) as HTMLButtonElement).disabled).toBe(true);
    resolveCreate({ ...entries[0], id: "entry-3", notes: "new entry" });
    await waitFor(() => expect(screen.queryByRole("button", { name: /saving/i })).toBeNull());
    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(2));

    fireEvent.click(screen.getByRole("button", { name: /delete deep work/i }));
    expect((await screen.findByRole("alert")).textContent).toContain("Delete unavailable");
  });

  it("resets to page one after a mutation instead of patching stale order", async () => {
    vi.mocked(createHistoryEntry).mockResolvedValue({ ...entries[0], id: "entry-3", startTime: "2026-08-13T10:00:00Z" });
    vi.mocked(getHistory)
      .mockResolvedValueOnce({ entries, nextCursor: "opaque-next" })
      .mockResolvedValueOnce({ entries: [{ ...entries[0], id: "entry-3" }], nextCursor: null });

    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /add entry/i }));
    fireEvent.change(screen.getByLabelText(/start time/i), { target: { value: "2026-08-12T14:30:00" } });
    fireEvent.change(screen.getByLabelText(/end time/i), { target: { value: "2026-08-12T14:45:00" } });
    fireEvent.click(screen.getByRole("button", { name: /save entry/i }));

    await waitFor(() => expect(getHistory).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("button", { name: /load more/i })).toBeNull();
  });

  it("remounts the form when switching from a new entry to an edit", async () => {
    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /add entry/i }));
    fireEvent.change(screen.getByLabelText(/notes/i), { target: { value: "should not leak" } });
    fireEvent.click(screen.getByRole("button", { name: /edit deep work/i }));

    expect((screen.getByLabelText(/notes/i) as HTMLInputElement).value).toBe("deep work");
    expect((screen.getByLabelText(/start time/i) as HTMLInputElement).value)
      .toBe(toDateTimeLocal(entries[0].startTime, "America/New_York").slice(0, -3));
    expect(screen.getByLabelText(/start time/i).getAttribute("step")).toBe("1");
  });

  it("associates server activity errors with the native select", async () => {
    vi.mocked(updateHistoryEntry).mockRejectedValueOnce(new ApiRequestError(
      "Activity is invalid", 400, "VALIDATION_ERROR", { activityType: "Choose Work or Rot." },
    ));
    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /edit deep work/i }));
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    const activity = await screen.findByLabelText(/activity/i);
    expect(activity.getAttribute("aria-invalid")).toBe("true");
    expect(activity.getAttribute("aria-describedby")).toBe("history-activityType-error");
    expect(screen.getByText("Choose Work or Rot.")).toBeTruthy();
  });

  it("deduplicates IDs when a cursor page repeats a row", async () => {
    vi.mocked(getHistory)
      .mockResolvedValueOnce({ entries, nextCursor: "opaque-next" })
      .mockResolvedValueOnce({ entries: [entries[0], secondEntry], nextCursor: null });

    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /load more/i }));

    await waitFor(() => expect(screen.getAllByRole("listitem")).toHaveLength(2));
  });

  it("loads the next page with the opaque cursor and keeps existing entries", async () => {
    vi.mocked(getHistory)
      .mockResolvedValueOnce({ entries, nextCursor: "opaque-next" })
      .mockResolvedValueOnce({ entries: [secondEntry], nextCursor: null });

    render(<HistoryPage />);
    await screen.findByText("deep work");
    fireEvent.click(screen.getByRole("button", { name: /load more/i }));

    await waitFor(() => expect(getHistory).toHaveBeenLastCalledWith("opaque-next"));
    expect(await screen.findByText(/rot · 15m/i)).toBeTruthy();
    expect(screen.getByText("deep work")).toBeTruthy();
  });
});
