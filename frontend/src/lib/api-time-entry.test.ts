import { beforeEach, describe, expect, it, vi } from "vitest";
import { stopSession } from "@/lib/api";

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

describe("time-entry API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("stops the requested entry through the ID-based endpoint", async () => {
    const response = {
      data: {
        id: "entry-1",
        activityType: "WORK",
        startTime: "2026-01-01T10:00:00Z",
        endTime: "2026-01-01T11:00:00Z",
        durationMinutes: 60,
        notes: null,
      },
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(response), { status: 200 }),
    );

    await stopSession("entry-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/time-entries/entry-1/stop",
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({
          Authorization: "Bearer redacted-token",
        }),
      }),
    );
  });
});
