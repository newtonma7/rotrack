import { expect, request, test, type APIRequestContext, type Page } from "@playwright/test";
import { currentSupabaseAuth } from "./support/critical-path";
import { installApiTargetGuard } from "./support/api-target";
import { e2eEnvironment as env } from "./support/environment";

const configured = Boolean(env.expectedApiUrl && env.userAStorageState && env.userBStorageState);
test.skip(!configured && !env.requireAuth, "M6.3 needs two disposable local users and a loopback Spring API.");
test.beforeEach(() => {
  if (!configured) throw new Error("M6.3 requires external auth states and an approved API target.");
  for (const url of [env.baseUrl, env.expectedApiUrl!]) {
    if (!["localhost", "127.0.0.1", "[::1]"].includes(new URL(url).hostname)) throw new Error("M6.3 permits loopback acceptance only.");
  }
});

async function username(page: Page): Promise<string> {
  return page.evaluate(() => {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key?.endsWith("-auth-token")) continue;
      const session: unknown = JSON.parse(localStorage.getItem(key) ?? "null");
      if (!session || typeof session !== "object" || !("user" in session)) continue;
      const user = session.user;
      if (!user || typeof user !== "object" || !("user_metadata" in user)) continue;
      const metadata = user.user_metadata;
      if (metadata && typeof metadata === "object" && "username" in metadata && typeof metadata.username === "string") return metadata.username;
    }
    throw new Error("Disposable fixture has no username metadata");
  });
}
async function apiFor(page: Page): Promise<APIRequestContext> {
  const { accessToken } = await currentSupabaseAuth(page);
  return request.newContext({ baseURL: env.expectedApiUrl + "/", extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });
}

test("M6.3 presence stays minimal, follows Work and opt-in, and refreshes on local start/stop", async ({ page, browser }) => {
  test.setTimeout(120000);
  const peerContext = await browser.newContext({ storageState: env.userBStorageState });
  const peerPage = await peerContext.newPage();
  const trackerPage = await page.context().newPage();
  const clients: APIRequestContext[] = [];
  let friendshipId: string | null = null;
  let originalPreferences: unknown;
  let peerName = "";
  let blocked = false;
  let peerEntry: string | null = null;
  try {
    for (const target of [page, peerPage, trackerPage]) await installApiTargetGuard(target, env.expectedApiUrl);
    await page.clock.install();
    await page.goto(env.baseUrl + "/friends"); await peerPage.goto(env.baseUrl + "/friends");
    peerName = await username(peerPage);
    const apiA = await apiFor(page); const apiB = await apiFor(peerPage); clients.push(apiA, apiB);
    for (const client of clients) {
      const active = (await (await client.get("time-entries/active")).json()).data;
      if (active) expect((await client.put(`time-entries/${active.id}/stop`)).ok()).toBe(true);
    }
    const preferences = (await (await apiB.get("preferences")).json()).data;
    originalPreferences = preferences;
    expect((await apiB.put("preferences", { data: { ...preferences, shareStudySummary: false, shareActiveStudyStatus: false } })).ok()).toBe(true);
    const invitation = await apiA.post("friend-requests", { data: { username: peerName } });
    expect(invitation.status()).toBe(201); friendshipId = (await invitation.json()).data.id;
    expect((await apiA.get(`social/presence/${peerName}`)).status()).toBe(404);
    expect((await apiB.post(`friend-requests/${friendshipId}/accept`)).ok()).toBe(true);
    await page.reload();
    await expect(page.getByText("Presence unavailable", { exact: true })).toBeVisible();
    const denied = await apiA.get(`social/presence/${peerName}`);
    expect(denied.status()).toBe(404); expect(denied.headers()["cache-control"]).toContain("no-store");
    expect((await denied.json()).path).toBe("/api/v1/social/presence/{username}");
    expect((await apiB.put("preferences", { data: { ...preferences, shareStudySummary: false, shareActiveStudyStatus: true } })).ok()).toBe(true);
    const reveal = page.getByRole("button", { name: `Retry presence for ${peerName}` });
    await reveal.focus(); await reveal.press("Enter");
    await expect(page.getByText("Not studying", { exact: true })).toBeVisible();
    const rot = await apiB.post("time-entries/start", { data: { activityType: "ROT", notes: "private label" } });
    expect(rot.status()).toBe(201); peerEntry = (await rot.json()).data.id;
    expect((await (await apiA.get(`social/presence/${peerName}`)).json()).data).toEqual({ studying: false });
    expect((await apiB.put(`time-entries/${peerEntry}/stop`)).ok()).toBe(true); peerEntry = null;
    const work = await apiB.post("time-entries/start", { data: { activityType: "WORK", notes: "private label" } });
    expect(work.status()).toBe(201); peerEntry = (await work.json()).data.id;
    // A remote friend's start reaches this tab via the real 30-second poll, not a mocked response.
    await expect(page.getByText("Studying now", { exact: true })).toBeVisible({ timeout: 35000 });
    const available = await apiA.get(`social/presence/${peerName}`);
    expect(available.status()).toBe(200); expect(available.headers()["cache-control"]).toContain("no-store");
    expect((await available.json()).data).toEqual({ studying: true });
    for (const width of [320, 390, 768, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      await expect(page.getByText("Studying now", { exact: true })).toBeVisible();
    }
    // Pause polling after its completed response. Only an event can now initiate the next read.
    await page.clock.pauseAt(new Date());
    await trackerPage.goto(env.baseUrl + "/tracker");
    const startRefresh = page.waitForResponse((r) => r.url() === env.expectedApiUrl + `/social/presence/${peerName}`);
    await trackerPage.getByRole("button", { name: "Work", exact: true }).click();
    await startRefresh;
    await expect(trackerPage.getByRole("button", { name: "Stop", exact: true })).toBeVisible();
    expect((await apiB.put(`time-entries/${peerEntry}/stop`)).ok()).toBe(true); peerEntry = null;
    const stopRefresh = page.waitForResponse((r) => r.url() === env.expectedApiUrl + `/social/presence/${peerName}`);
    await trackerPage.getByRole("button", { name: "Stop", exact: true }).click();
    await stopRefresh;
    await expect(page.getByText("Not studying", { exact: true })).toBeVisible({ timeout: 5000 });
    expect((await apiB.put("preferences", { data: { ...preferences, shareActiveStudyStatus: false } })).ok()).toBe(true);
    await page.reload(); await expect(page.getByText("Presence unavailable", { exact: true })).toBeVisible();
    page.once("dialog", (dialog) => dialog.accept()); blocked = true;
    await page.getByRole("button", { name: `Block ${peerName}`, exact: true }).click();
    await expect(page.getByRole("button", { name: `Unblock ${peerName}`, exact: true })).toBeVisible();
    expect((await apiA.get(`social/presence/${peerName}`)).status()).toBe(404);
    await expect(page.getByLabel(`Study presence for ${peerName}`)).toHaveCount(0);
  } finally {
    if (peerEntry) await clients[1]?.put(`time-entries/${peerEntry}/stop`);
    if (clients[0]) {
      const active = (await (await clients[0].get("time-entries/active")).json()).data;
      if (active) await clients[0].put(`time-entries/${active.id}/stop`);
      if (blocked) await clients[0].delete(`blocks/${peerName}`);
      else if (friendshipId) await clients[0].delete(`friends/${friendshipId}`);
    }
    if (originalPreferences) await clients[1]?.put("preferences", { data: originalPreferences });
    await Promise.all(clients.map((client) => client.dispose()));
    await trackerPage.close(); await peerContext.close();
  }
});
