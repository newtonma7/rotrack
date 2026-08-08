import { expect, type Page, type Response } from "@playwright/test";

export type ActivityType = "WORK" | "ROT";

export type TimeEntry = {
  id: string;
  activityType: ActivityType;
  startTime: string;
  endTime: string | null;
  durationSeconds: number | null;
};

export type StartedTimeEntry = TimeEntry & { apiOrigin: string };

export type DashboardStats = {
  range: { start: string; end: string; timeZone: string };
  totalSeconds: Record<ActivityType, number>;
  daily: Array<{ localDate: string; workSeconds: number; rotSeconds: number }>;
  recentSessions: TimeEntry[];
};

type ApiEnvelope<T> = { data: T };

const API_PREFIX = "/api/v1";

function apiResponse(
  page: Page,
  method: string,
  pathname: string | RegExp,
): Promise<Response> {
  return page.waitForResponse((response) => {
    const url = new URL(response.url());
    const pathMatches =
      typeof pathname === "string" ? url.pathname === pathname : pathname.test(url.pathname);
    return response.request().method() === method && pathMatches;
  });
}

async function responseData<T>(response: Response, expectedStatus: number): Promise<T> {
  expect(response.status()).toBe(expectedStatus);
  const body = (await response.json()) as ApiEnvelope<T>;
  expect(body).toHaveProperty("data");
  return body.data;
}

export async function openTracker(page: Page): Promise<void> {
  await page.goto("/tracker");
  await expect(page).toHaveURL(/\/tracker$/);
  await expect(page.getByText("Active session", { exact: true })).toBeVisible();

  const stop = page.getByRole("button", { name: "Stop session" });
  const work = page.getByRole("button", { name: "Work" });
  const rot = page.getByRole("button", { name: "Rot" });
  await expect.poll(async () => {
    // The hook initially renders "no active session" while GET /active is pending.
    // Wait for either a restored stop control or an enabled activity control.
    return (await stop.isVisible()) || (await work.isEnabled()) || (await rot.isEnabled());
  }).toBe(true);
  await expect(
    page.getByText("no active session", { exact: true }).or(stop),
  ).toBeVisible();
}

/** Test accounts are disposable: clear only their own stale active entry before a scenario. */
export async function stopActiveSessionIfPresent(page: Page): Promise<void> {
  await openTracker(page);
  const stop = page.getByRole("button", { name: "Stop session" });
  if (await stop.isVisible()) {
    const stopped = apiResponse(page, "PUT", /\/api\/v1\/time-entries\/[^/]+\/stop$/);
    await stop.click();
    await responseData<TimeEntry>(await stopped, 200);
  }
  await expect(page.getByText("no active session", { exact: true })).toBeVisible();
}

export async function startSession(page: Page, activityType: ActivityType): Promise<StartedTimeEntry> {
  const started = apiResponse(page, "POST", `${API_PREFIX}/time-entries/start`);
  await page.getByRole("button", { name: activityType === "WORK" ? "Work" : "Rot" }).click();
  const startedResponse = await started;
  const entry = await responseData<TimeEntry>(startedResponse, 201);
  expect(entry.activityType).toBe(activityType);
  expect(entry.endTime).toBeNull();
  await expect(page.getByText(`tracking ${activityType.toLowerCase()}`, { exact: true })).toBeVisible();
  return { ...entry, apiOrigin: new URL(startedResponse.url()).origin };
}

export async function waitForRecordedSecond(page: Page): Promise<void> {
  await expect(page.getByText(/^\d{2}:\d{2}:(?!00)\d{2}$/)).toBeVisible({ timeout: 5_000 });
}

export async function restoreActiveSession(page: Page): Promise<TimeEntry> {
  const active = apiResponse(page, "GET", `${API_PREFIX}/time-entries/active`);
  await page.reload();
  const entry = await responseData<TimeEntry>(await active, 200);
  await expect(page.getByRole("button", { name: "Stop session" })).toBeVisible();
  return entry;
}

export async function stopSession(page: Page): Promise<TimeEntry> {
  const stopped = apiResponse(page, "PUT", /\/api\/v1\/time-entries\/[^/]+\/stop$/);
  await page.getByRole("button", { name: "Stop session" }).click();
  const entry = await responseData<TimeEntry>(await stopped, 200);
  expect(entry.endTime).not.toBeNull();
  expect(entry.durationSeconds).not.toBeNull();
  await expect(page.getByText("no active session", { exact: true })).toBeVisible();
  return entry;
}

export async function openDashboard(page: Page): Promise<DashboardStats> {
  const dashboard = apiResponse(page, "GET", `${API_PREFIX}/dashboard/stats`);
  await page.goto("/dashboard");
  const stats = await responseData<DashboardStats>(await dashboard, 200);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByText("your last seven local days", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Summary" })).toBeVisible();
  return stats;
}

async function readSupabaseAuth(page: Page): Promise<{ accessToken: string; userId: string }> {
  return page.evaluate(() => {
    const findValue = (value: unknown, key: "access_token" | "id"): string | null => {
      if (!value || typeof value !== "object") return null;
      if (key in value && typeof (value as Record<string, unknown>)[key] === "string") {
        return (value as Record<string, string>)[key];
      }
      for (const child of Object.values(value)) {
        const found = findValue(child, key);
        if (found) return found;
      }
      return null;
    };

    for (let index = 0; index < localStorage.length; index += 1) {
      const storageKey = localStorage.key(index);
      if (!storageKey?.endsWith("-auth-token")) continue;
      const raw = localStorage.getItem(storageKey);
      if (!raw) continue;
      const auth = JSON.parse(raw) as unknown;
      const accessToken = findValue(auth, "access_token");
      const userId = findValue(auth, "id");
      if (accessToken && userId) return { accessToken, userId };
    }
    throw new Error("No Supabase browser auth session was found in the configured storage state.");
  });
}

export async function authenticatedUserId(page: Page): Promise<string> {
  return (await readSupabaseAuth(page)).userId;
}

/**
 * Exercise the ownership boundary with User B's token without ever returning or logging it.
 * Playwright tracing is disabled because network traces can contain Authorization headers.
 */
export async function tryStopEntryAsCurrentUser(
  page: Page,
  apiOrigin: string,
  entryId: string,
): Promise<{ status: number; code: string | null }> {
  const auth = await readSupabaseAuth(page);
  return page.evaluate(
    async ({ accessToken, apiUrl, id }) => {
      const response = await fetch(
        `${apiUrl}/api/v1/time-entries/${encodeURIComponent(id)}/stop`,
        {
          method: "PUT",
          headers: { Authorization: `Bearer ${accessToken}` },
        },
      );
      const body = (await response.json()) as { error?: { code?: string } };
      return { status: response.status, code: body.error?.code ?? null };
    },
    { accessToken: auth.accessToken, apiUrl: apiOrigin, id: entryId },
  );
}
