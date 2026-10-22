/* @vitest-environment jsdom */

import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getActiveSession, startSession, stopSession } from "@/lib/api";
import { useTimeTracking } from "@/hooks/useTimeTracking";
import type { TimeEntry } from "@/types/time-entry";

vi.mock("@/lib/api", () => ({
  getActiveSession: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
}));

const activeEntry: TimeEntry = {
  id: "entry-1",
  activityType: "WORK",
  startTime: "2026-01-01T10:00:00Z",
  endTime: null,
  durationSeconds: null,
  notes: null,
};

const stoppedEntry: TimeEntry = {
  ...activeEntry,
  endTime: "2026-01-01T11:00:00Z",
  durationSeconds: 3600,
};

describe("useTimeTracking", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getActiveSession).mockResolvedValue(null);
  });

  afterEach(() => { cleanup(); vi.useRealTimers(); });

  it("renders restored elapsed time immediately, ticks, and resets for explicit stop/start", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-01-01T11:00:00Z"));
    vi.mocked(getActiveSession).mockResolvedValue(activeEntry);
    vi.mocked(stopSession).mockResolvedValue(stoppedEntry);
    vi.mocked(startSession).mockResolvedValue({ ...activeEntry, id: "entry-2", activityType: "ROT", startTime: "2026-01-01T11:00:01Z" });
    const { result } = renderHook(() => useTimeTracking());
    expect(result.current.loading).toBe(true);
    await act(async () => {});
    expect(result.current.elapsed).toBe("01:00:00");
    await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
    expect(result.current.elapsed).toBe("01:00:01");
    await act(async () => { await result.current.stop(); });
    expect(result.current.elapsed).toBe("00:00:00");
    await act(async () => { await result.current.start("ROT"); });
    expect(result.current.elapsed).toBe("00:00:00");
    expect(startSession).toHaveBeenCalledExactlyOnceWith("ROT");
    expect(result.current.loading).toBe(false);
  });

  it("restores the server-owned active entry on mount", async () => {
    vi.mocked(getActiveSession).mockResolvedValue(activeEntry);

    const { result } = renderHook(() => useTimeTracking());

    await waitFor(() => expect(result.current.activeEntry).toEqual(activeEntry));
    expect(getActiveSession).toHaveBeenCalledOnce();
  });

  it("stops the restored entry by id and clears it after success", async () => {
    vi.mocked(getActiveSession).mockResolvedValue(activeEntry);
    vi.mocked(stopSession).mockResolvedValue(stoppedEntry);
    const { result } = renderHook(() => useTimeTracking());

    await waitFor(() => expect(result.current.activeEntry).toEqual(activeEntry));

    await act(async () => {
      await result.current.stop();
    });

    expect(stopSession).toHaveBeenCalledWith("entry-1");
    expect(result.current.activeEntry).toBeNull();
  });

  it("keeps the active entry when an explicit stop fails", async () => {
    vi.mocked(getActiveSession).mockResolvedValue(activeEntry);
    vi.mocked(stopSession).mockRejectedValue(new Error("stop failed"));
    const { result } = renderHook(() => useTimeTracking());

    await waitFor(() => expect(result.current.activeEntry).toEqual(activeEntry));

    await act(async () => {
      await result.current.stop();
    });

    expect(result.current.activeEntry).toEqual(activeEntry);
    expect(result.current.error).toBe("stop failed");
  });

  it("does not register an unload auto-stop handler", () => {
    const addEventListener = vi.spyOn(window, "addEventListener");

    renderHook(() => useTimeTracking());

    expect(addEventListener).not.toHaveBeenCalledWith("pagehide", expect.any(Function));
    addEventListener.mockRestore();
  });
});
