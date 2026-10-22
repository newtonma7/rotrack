import { beforeEach, describe, expect, it, vi } from "vitest";
import { getDailyLog, getDailyLogs, updateReflection } from "@/lib/api";
import type { DailyLogSummaryPage, Reflection } from "@/types/daily-logs";

vi.mock("@/lib/supabase/client", () => ({ supabase: { auth: { getSession: vi.fn().mockResolvedValue({ data: { session: { access_token: "token", expires_at: Math.floor(Date.now() / 1000) + 3600 } } }), refreshSession: vi.fn() } } }));
const page: DailyLogSummaryPage = { range: { start: "2026-08-01", end: "2026-08-02", timeZone: "America/New_York" }, days: [] };
const reflection: Reflection = { contentJson: { schemaVersion: 1, document: { type: "doc", content: [] } }, contentText: "", contentSchemaVersion: 1, version: 1, timeZoneAtCreation: "America/New_York", updatedAt: "2026-08-01T00:00:00Z" };
const response = (data: unknown) => new Response(JSON.stringify({ data }), { status: 200 });

describe("daily logs API", () => {
  beforeEach(() => vi.restoreAllMocks());
  it("encodes complete date ranges and effective timezone", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(response(page));
    await expect(getDailyLogs({ start: "2026-08-01", end: "2026-08-02", timeZone: "America/New_York" })).resolves.toEqual(page);
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/daily-logs?start=2026-08-01&end=2026-08-02&timeZone=America%2FNew_York");
  });
  it("gets a stable date detail and sends creation idempotency only on first write", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async () => response({ reflection }));
    await getDailyLog("2026-08-01", "America/New_York");
    await updateReflection("2026-08-01", { contentJson: reflection.contentJson, expectedVersion: 0 }, "stable-key", "America/New_York");
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/daily-logs/2026-08-01?timeZone=America%2FNew_York");
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ method: "PUT", headers: expect.objectContaining({ "Idempotency-Key": "stable-key" }) }));
  });
  it("omits the creation key when updating an existing reflection", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(response(reflection));
    await updateReflection("2026-08-01", { contentJson: reflection.contentJson, expectedVersion: 1 }, undefined, "UTC");
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ method: "PUT", headers: expect.not.objectContaining({ "Idempotency-Key": expect.anything() }) }));
  });
});
