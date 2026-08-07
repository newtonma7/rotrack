import { beforeEach, describe, expect, it, vi } from "vitest";
import { getDashboardStats } from "@/lib/api";

vi.mock("@/lib/supabase/client", () => ({
  supabase: {
    auth: {
      getUser: vi.fn().mockResolvedValue({ data: { user: { id: "user-1" } }, error: null }),
      getSession: vi.fn().mockResolvedValue({
        data: { session: { access_token: "redacted-token", expires_at: Math.floor(Date.now() / 1000) + 3600 } },
      }),
      refreshSession: vi.fn(),
    },
  },
}));

describe("dashboard API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("sends the IANA timezone and optional half-open local date range", async () => {
    const response = {
      data: {
        range: {
          start: "2026-03-08T05:00:00Z",
          end: "2026-03-09T04:00:00Z",
          timeZone: "America/New_York",
        },
        totalSeconds: { WORK: 82_800, ROT: 0 },
        daily: [{ localDate: "2026-03-08", workSeconds: 82_800, rotSeconds: 0 }],
        recentSessions: [],
        productivityScore: 100,
      },
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(response), { status: 200 }),
    );

    const result = await getDashboardStats({
      timeZone: "America/New_York",
      start: "2026-03-08",
      end: "2026-03-09",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/dashboard/stats?timeZone=America%2FNew_York&start=2026-03-08&end=2026-03-09",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer redacted-token" }),
      }),
    );
    expect(result.totalSeconds.WORK).toBe(82_800);
  });
});
