import { expect, request, test, type APIRequestContext, type Page } from "@playwright/test";
import { currentSupabaseAuth } from "./support/critical-path";
import { installApiTargetGuard } from "./support/api-target";
import { e2eEnvironment as env } from "./support/environment";

const configured = Boolean(env.expectedApiUrl && env.userAStorageState && env.userBStorageState);
test.skip(!configured && !env.requireAuth, "M6.2 needs two distinct disposable local users and a loopback Spring API.");
test.beforeEach(() => {
  if (!configured) throw new Error("M6.2 requires two external storage states and an approved API target.");
  for (const url of [env.baseUrl, env.expectedApiUrl!]) {
    if (!["localhost", "127.0.0.1", "[::1]"].includes(new URL(url).hostname)) throw new Error("M6.2 acceptance permits loopback targets only.");
  }
});

async function username(page: Page): Promise<string> {
  return page.evaluate(() => {
    function find(value: unknown): string | null {
      if (!value || typeof value !== "object") return null;
      if ("username" in value && typeof value.username === "string" && /^[a-z0-9_]{3,24}$/.test(value.username)) return value.username;
      for (const child of Object.values(value)) { const found = find(child); if (found) return found; }
      return null;
    }
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index);
      if (!key?.endsWith("-auth-token")) continue;
      const found = find(JSON.parse(localStorage.getItem(key) ?? "null"));
      if (found) return found;
    }
    throw new Error("Disposable fixture has no canonical username metadata.");
  });
}

async function apiFor(page: Page): Promise<APIRequestContext> {
  const { accessToken } = await currentSupabaseAuth(page);
  return request.newContext({ baseURL: env.expectedApiUrl + "/", extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });
}

test("M6.2 reveals only the opted-in accepted friend's allowlisted summary and clears it after revoke/block", async ({ page, browser }) => {
  test.setTimeout(90000);
  const friendContext = await browser.newContext({ storageState: env.userBStorageState });
  const friendPage = await friendContext.newPage();
  const clients: APIRequestContext[] = [];
  let friendshipId: string | null = null;
  let blocked = false;
  let originalPreferences: unknown;
  let peerName = "";
  try {
    await installApiTargetGuard(page, env.expectedApiUrl);
    await installApiTargetGuard(friendPage, env.expectedApiUrl);
    await page.goto(env.baseUrl + "/friends"); await friendPage.goto(env.baseUrl + "/friends");
    const nameA = await username(page); peerName = await username(friendPage);
    expect(nameA).not.toBe(peerName);
    const apiA = await apiFor(page); const apiB = await apiFor(friendPage); clients.push(apiA, apiB);
    const preferencesResponse = await apiB.get("preferences");
    expect(preferencesResponse.ok()).toBe(true);
    const preferenceEnvelope = await preferencesResponse.json();
    originalPreferences = preferenceEnvelope.data;
    await apiB.put("preferences", { data: { ...preferenceEnvelope.data, shareStudySummary: false } });

    const requestResponse = await apiA.post("friend-requests", { data: { username: peerName } });
    expect(requestResponse.status()).toBe(201);
    const requestBody = await requestResponse.json(); friendshipId = requestBody.data.id;
    expect(Object.keys(requestBody.data).sort()).toEqual(["id", "username"]);
    expect((await apiB.post(`friend-requests/${friendshipId}/accept`)).status()).toBe(200);

    await page.reload();
    await expect(page.getByRole("button", { name: `Remove ${peerName}`, exact: true })).toBeVisible();
    await page.getByRole("button", { name: `Show study summary for ${peerName}`, exact: true }).click();
    const hidden = await page.waitForResponse((response) => response.url() === env.expectedApiUrl + `/social/summaries/${encodeURIComponent(peerName)}`);
    expect(hidden.status()).toBe(404);
    await expect(page.getByText("Summary unavailable.", { exact: true })).toBeVisible();
    expect(await hidden.headerValue("cache-control")).toContain("no-store");

    await apiB.put("preferences", { data: { ...preferenceEnvelope.data, shareStudySummary: true } });
    await page.getByRole("button", { name: `Retry study summary for ${peerName}`, exact: true }).click();
    const response = await page.waitForResponse((candidate) => candidate.url() === env.expectedApiUrl + `/social/summaries/${encodeURIComponent(peerName)}` && candidate.status() === 200);
    const payload = await response.json();
    expect(Object.keys(payload.data).sort()).toEqual(["streakCapped", "studyDayStreak", "workSeconds"]);
    expect(typeof payload.data.workSeconds).toBe("number");
    expect(typeof payload.data.studyDayStreak).toBe("number");
    expect(typeof payload.data.streakCapped).toBe("boolean");
    expect(await response.headerValue("cache-control")).toContain("no-store");
    await expect(page.getByText(/work · last 7 days/)).toBeVisible();

    await apiB.put("preferences", { data: { ...preferenceEnvelope.data, shareStudySummary: false } });
    await page.getByRole("button", { name: `Refresh study summary for ${peerName}`, exact: true }).click();
    const revoked = await page.waitForResponse((candidate) => candidate.url() === env.expectedApiUrl + `/social/summaries/${encodeURIComponent(peerName)}` && candidate.status() === 404);
    expect(await revoked.headerValue("cache-control")).toContain("no-store");
    await expect(page.getByText("Summary unavailable.", { exact: true })).toBeVisible();

    await apiB.put("preferences", { data: { ...preferenceEnvelope.data, shareStudySummary: true } });
    await page.getByRole("button", { name: `Retry study summary for ${peerName}`, exact: true }).click();
    await expect(page.getByText(/work · last 7 days/)).toBeVisible();
    page.once("dialog", (dialog) => dialog.accept());
    blocked = true;
    await page.getByRole("button", { name: `Block ${peerName}`, exact: true }).click();
    await expect(page.getByRole("button", { name: `Unblock ${peerName}`, exact: true })).toBeVisible();
    expect((await apiA.get(`social/summaries/${encodeURIComponent(peerName)}`)).status()).toBe(404);
    expect((await apiA.get(`social/summaries/${encodeURIComponent(peerName)}`)).headers()["cache-control"]).toContain("no-store");
  } finally {
    if (blocked) await clients[0]?.delete(`blocks/${encodeURIComponent(peerName)}`).catch(() => undefined);
    if (friendshipId && !blocked) await clients[0]?.delete(`friends/${friendshipId}`).catch(() => undefined);
    if (originalPreferences) await clients[1]?.put("preferences", { data: originalPreferences }).catch(() => undefined);
    await Promise.all(clients.map((client) => client.dispose()));
    await friendContext.close();
  }
});
