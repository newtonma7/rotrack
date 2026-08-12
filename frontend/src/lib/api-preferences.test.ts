import { beforeEach, describe, expect, it, vi } from "vitest";
import { getPreferences, updatePreferences } from "@/lib/api";
import type { UserPreferences } from "@/types/preferences";

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

const preferences: UserPreferences = {
  timeZone: "America/New_York",
  dailyWorkGoalMinutes: 90,
  shareStudySummary: false,
  shareActiveStudyStatus: true,
};

describe("preferences API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("gets the authenticated user's preferences", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ data: preferences }), { status: 200 }),
    );

    await expect(getPreferences()).resolves.toEqual(preferences);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/preferences",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer redacted-token" }),
      }),
    );
  });

  it("updates preferences with the typed JSON contract", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ data: preferences }), { status: 200 }),
    );

    await expect(updatePreferences(preferences)).resolves.toEqual(preferences);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/preferences",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify(preferences),
      }),
    );
  });
});
