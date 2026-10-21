// @vitest-environment jsdom
import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { updateReflection } from "@/lib/api";
import { useReflectionAutosave } from "@/hooks/useReflectionAutosave";
import type { Reflection } from "@/types/daily-logs";

vi.mock("@/lib/api", () => ({ updateReflection: vi.fn() }));
const empty = { schemaVersion: 1 as const, document: { type: "doc" as const, content: [] } };
const doc = (text: string) => ({ schemaVersion: 1 as const, document: { type: "doc" as const, content: [{ type: "paragraph" as const, content: [{ type: "text" as const, text }] }] } });
const saved: Reflection = { contentJson: doc("today mattered"), contentText: "today mattered", contentSchemaVersion: 1, version: 1, timeZoneAtCreation: "UTC", updatedAt: "2026-08-01T00:00:00Z" };

afterEach(() => { cleanup(); vi.useRealTimers(); vi.resetAllMocks(); });

describe("Reflection autosave", () => {
  it("does not create an empty Reflection and coalesces meaningful edits", async () => {
    vi.useFakeTimers(); vi.mocked(updateReflection).mockResolvedValue(saved);
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: null }));
    await act(async () => { await vi.advanceTimersByTimeAsync(900); });
    expect(updateReflection).not.toHaveBeenCalled();
    act(() => { result.current.setContent(doc("first")); result.current.setContent(doc("today mattered")); });
    expect(result.current.status).toBe("Waiting");
    await act(async () => { await vi.advanceTimersByTimeAsync(749); });
    expect(updateReflection).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(updateReflection).toHaveBeenCalledTimes(1);
    expect(updateReflection).toHaveBeenCalledWith("2026-08-01", { contentJson: doc("today mattered"), expectedVersion: 0 }, expect.any(String), "UTC");
    expect(result.current.status).toBe("Saved");
  });

  it("keeps current local content after a version conflict", async () => {
    vi.useFakeTimers();
    const { ApiRequestError } = await import("@/lib/api-errors");
    vi.mocked(updateReflection).mockRejectedValue(new ApiRequestError("stale", 409, "RICH_TEXT_VERSION_CONFLICT"));
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(doc("my unsubmitted thought")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(result.current.status).toBe("Conflict");
    expect(result.current.document).toEqual(doc("my unsubmitted thought"));
    expect(result.current.dirty).toBe(true);
  });

  it("replays a frozen first-create request before saving newer edits after a lost response", async () => {
    vi.useFakeTimers();
    vi.mocked(updateReflection).mockRejectedValueOnce(new Error("response lost")).mockResolvedValueOnce(saved).mockResolvedValueOnce({ ...saved, contentJson: doc("newer edit"), contentText: "newer edit", version: 2 });
    const { result, rerender } = renderHook(({ timeZone }: { timeZone: string }) => useReflectionAutosave({ localDate: "2026-08-01", timeZone, initialReflection: null }), { initialProps: { timeZone: "America/New_York" } });
    act(() => result.current.setContent(doc("first request")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(result.current.status).toBe("Offline");
    const firstKey = vi.mocked(updateReflection).mock.calls[0][2];
    act(() => result.current.setContent(doc("newer edit")));
    rerender({ timeZone: "UTC" });
    await act(async () => { await result.current.flush(); });
    expect(vi.mocked(updateReflection).mock.calls[1]).toEqual(["2026-08-01", { contentJson: doc("first request"), expectedVersion: 0 }, firstKey, "America/New_York"]);
    expect(vi.mocked(updateReflection).mock.calls[2]).toEqual(["2026-08-01", { contentJson: doc("newer edit"), expectedVersion: 1 }, undefined, "UTC"]);
    expect(result.current.dirty).toBe(false);
  });

  it("drains edits made during an in-flight save before flush resolves", async () => {
    vi.useFakeTimers();
    let finish!: (value: Reflection) => void;
    vi.mocked(updateReflection).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; })).mockResolvedValueOnce({ ...saved, contentJson: doc("second"), contentText: "second", version: 2 });
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(doc("first")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); await Promise.resolve(); });
    expect(finish).toBeTypeOf("function");
    let flushed!: Promise<boolean>;
    act(() => { flushed = result.current.flush(); result.current.setContent(doc("second")); });
    await act(async () => { finish({ ...saved, contentJson: doc("first"), contentText: "first", version: 2 }); await flushed; });
    expect(updateReflection).toHaveBeenCalledTimes(2);
    expect(vi.mocked(updateReflection).mock.calls[1][1]).toEqual({ contentJson: doc("second"), expectedVersion: 2 });
    expect(result.current.dirty).toBe(false);
  });

  it("does not retry an explicit conflict when the browser comes online", async () => {
    vi.useFakeTimers();
    const { ApiRequestError } = await import("@/lib/api-errors");
    vi.mocked(updateReflection).mockRejectedValue(new ApiRequestError("stale", 409, "RICH_TEXT_VERSION_CONFLICT"));
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(doc("local")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    act(() => window.dispatchEvent(new Event("online")));
    await act(async () => { await Promise.resolve(); });
    expect(updateReflection).toHaveBeenCalledTimes(1);
    expect(result.current.document).toEqual(doc("local"));
  });

  it("retries a rate-limited save once per edit or explicit retry", async () => {
    vi.useFakeTimers();
    const { ApiRequestError } = await import("@/lib/api-errors");
    vi.mocked(updateReflection).mockRejectedValue(new ApiRequestError("limit", 429, "RATE_LIMITED", {}, 1000));
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(doc("waiting edit")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
    expect(updateReflection).toHaveBeenCalledTimes(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(updateReflection).toHaveBeenCalledTimes(2);
    expect(result.current.dirty).toBe(true);
    act(() => result.current.retry());
    await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
    expect(updateReflection).toHaveBeenCalledTimes(4);
  });

  it("waits for an explicit retry after a network failure even when the browser reports online", async () => {
    vi.useFakeTimers();
    vi.mocked(updateReflection).mockRejectedValueOnce(new Error("offline")).mockResolvedValue(saved);
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(doc("waiting edit")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    act(() => window.dispatchEvent(new Event("online")));
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(updateReflection).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe("Offline");
    act(() => result.current.retry());
    await act(async () => { await Promise.resolve(); });
    expect(updateReflection).toHaveBeenCalledTimes(2);
    expect(result.current.status).toBe("Saved");
  });

  it("blocks local writes above the 256 KiB serialized document limit", async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: null }));
    act(() => result.current.setContent(doc("x".repeat(262_145))));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(updateReflection).not.toHaveBeenCalled();
    expect(result.current.status).toBe("Waiting");
    expect(result.current.error).toContain("too large");
    expect(result.current.dirty).toBe(true);
  });

  it("stops on a creation-key conflict without changing the key or overwriting", async () => {
    vi.useFakeTimers();
    const { ApiRequestError } = await import("@/lib/api-errors");
    vi.mocked(updateReflection).mockRejectedValue(new ApiRequestError("key reused", 409, "IDEMPOTENCY_CONFLICT"));
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: null }));
    act(() => result.current.setContent(doc("keep my draft")));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(result.current.status).toBe("Conflict");
    expect(result.current.document).toEqual(doc("keep my draft"));
    act(() => result.current.retry());
    expect(updateReflection).toHaveBeenCalledTimes(1);
  });

  it("copies rich HTML and plain text while preserving editor formatting", async () => {
    vi.useFakeTimers();
    const write = vi.fn();
    class TestClipboardItem { constructor(readonly values: Record<string, Blob>) {} }
    vi.stubGlobal("ClipboardItem", TestClipboardItem);
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { write } });
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: null }));
    const formatted = { schemaVersion: 1 as const, document: { type: "doc" as const, content: [{ type: "heading" as const, attrs: { level: 2 as const }, content: [{ type: "text" as const, text: "format me", marks: [{ type: "bold" as const }] }] }] } };
    act(() => result.current.setContent(formatted));
    await act(async () => { await result.current.copy(); });
    const item = write.mock.calls[0][0][0] as TestClipboardItem;
    await expect(item.values["text/html"].text()).resolves.toContain("<h2><strong>format me</strong></h2>");
    await expect(item.values["text/plain"].text()).resolves.toContain("format me");
  });

  it("keeps an empty cleared document as an update to the existing Reflection", async () => {
    vi.useFakeTimers(); vi.mocked(updateReflection).mockResolvedValue({ ...saved, contentJson: empty, contentText: "", version: 2 });
    const { result } = renderHook(() => useReflectionAutosave({ localDate: "2026-08-01", timeZone: "UTC", initialReflection: saved }));
    act(() => result.current.setContent(empty));
    await act(async () => { await vi.advanceTimersByTimeAsync(750); });
    expect(updateReflection).toHaveBeenCalledWith("2026-08-01", { contentJson: empty, expectedVersion: 1 }, undefined, "UTC");
    expect(result.current.reflection?.version).toBe(2);
  });
});
