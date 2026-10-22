import { expect, request, test, type APIRequestContext, type Page } from "@playwright/test";
import { currentSupabaseAuth } from "./support/critical-path";
import { installApiTargetGuard } from "./support/api-target";
import { e2eEnvironment as env } from "./support/environment";

const configured = Boolean(env.expectedApiUrl && env.userAStorageState && env.userBStorageState && env.userCStorageState);
test.skip(!configured && !env.requireAuth, "M6.1 needs three distinct disposable local users.");
test.beforeEach(() => {
  if (!configured) throw new Error("M6.1 requires three external storage states and an approved API target.");
  for (const url of [env.baseUrl, env.expectedApiUrl!]) {
    if (!["localhost", "127.0.0.1", "[::1]"].includes(new URL(url).hostname)) throw new Error("M6.1 acceptance permits loopback targets only.");
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

async function client(page: Page): Promise<APIRequestContext> {
  const { accessToken } = await currentSupabaseAuth(page);
  return request.newContext({ baseURL: env.expectedApiUrl + "/", extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });
}

async function send(page: Page, peer: string): Promise<string> {
  await page.getByLabel("Username", { exact: true }).fill(peer);
  const response = page.waitForResponse((candidate) => candidate.url() === env.expectedApiUrl + "/friend-requests" && candidate.request().method() === "POST");
  const add = page.getByRole("button", { name: `Add ${peer}`, exact: true });
  await add.focus(); await add.press("Enter");
  const created = await response;
  expect(created.status()).toBe(201);
  const body = await created.json();
  expect(Object.keys(body.data).sort()).toEqual(["id", "username"]);
  expect(body.data.username).toBe(peer);
  expect(typeof body.data.id).toBe("string");
  await expect(page.getByText(`Request sent to @${peer}.`, { exact: true })).toBeVisible();
  return body.data.id;
}

async function refresh(page: Page, view: "Friends" | "Incoming" | "Sent") {
  await page.getByRole("button", { name: "Refresh lists", exact: true }).click();
  await page.getByRole("button", { name: view, exact: true }).click();
}

test("M6.1 explicit two-way lifecycle, third-user isolation, directional blocks, search budget, and responsive keyboard UI", async ({ page, browser }) => {
  test.setTimeout(90000);
  const b = await browser.newContext({ storageState: env.userBStorageState });
  const c = await browser.newContext({ storageState: env.userCStorageState });
  const pageB = await b.newPage(); const pageC = await c.newPage();
  const clients: APIRequestContext[] = [];
  const createdIds: string[] = [];
  let nameA = ""; let nameB = "";
  try {
    for (const target of [page, pageB, pageC]) {
      await installApiTargetGuard(target, env.expectedApiUrl);
      await target.goto(env.baseUrl + "/friends");
      await expect(target.getByRole("heading", { name: "better together." })).toBeVisible();
    }
    nameA = await username(page); nameB = await username(pageB);
    expect(nameA).not.toBe(nameB);
    const apiA = await client(page); const apiB = await client(pageB); const apiC = await client(pageC);
    clients.push(apiA, apiB, apiC);
    for (const [api, peer] of [[apiA, nameB], [apiB, nameA]] as const) {
      const results = await (await api.get("users/search", { params: { q: peer } })).json();
      expect(results.data.items).toContainEqual({ username: peer, relationship: "NONE" });
    }

    const first = await send(page, nameB); createdIds.push(first);
    expect((await apiB.post("friend-requests", { data: { username: nameA } })).status()).toBe(409);
    expect((await apiA.post(`friend-requests/${first}/accept`)).status()).toBe(409);
    const foreign = await apiC.post(`friend-requests/${first}/accept`);
    const missing = await apiC.post(`friend-requests/${crypto.randomUUID()}/accept`);
    expect(foreign.status()).toBe(404);
    expect((await foreign.json()).error).toEqual((await missing.json()).error);
    expect((await apiC.delete(`friend-requests/${first}`)).status()).toBe(404);
    expect((await apiA.post("friend-requests", { data: { username: nameA } })).status()).toBe(400);
    expect((await apiA.post("friend-requests", { data: { username: nameB, user_id: "forged" } })).status()).toBe(400);
    await refresh(page, "Sent");
    await page.getByRole("button", { name: `Cancel request to ${nameB}`, exact: true }).click();
    await expect(page.getByText("Request cancelled.", { exact: true })).toBeVisible();

    const second = await send(pageB, nameA); createdIds.push(second);
    await refresh(page, "Incoming");
    await page.getByRole("button", { name: `Decline ${nameB}`, exact: true }).click();
    await expect(page.getByText("Request declined.", { exact: true })).toBeVisible();

    await page.getByLabel("Username", { exact: true }).fill("");
    const accepted = await send(page, nameB); createdIds.push(accepted);
    await refresh(pageB, "Incoming");
    await pageB.getByRole("button", { name: `Accept ${nameA}`, exact: true }).click();
    await expect(pageB.getByText(`You're now friends with @${nameA}.`, { exact: true })).toBeVisible();
    expect((await apiB.post(`friend-requests/${accepted}/accept`)).status()).toBe(200);
    await page.reload();
    await expect(page.getByRole("button", { name: `Remove ${nameB}`, exact: true })).toBeVisible();
    for (const width of [320, 390, 768, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const links = page.getByRole("navigation", { name: "Application" });
      await expect(links.getByRole("link", { name: "settings", exact: true })).toBeVisible();
      const boxes = await links.locator("a, [aria-current]").evaluateAll((elements) => elements.map((element) => {
        const rect = element.getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
      }));
      for (let i = 0; i < boxes.length; i++) {
        expect(boxes[i].left).toBeGreaterThanOrEqual(0);
        expect(boxes[i].right).toBeLessThanOrEqual(width);
        for (const other of boxes.slice(i + 1)) {
          const overlaps = boxes[i].left < other.right && boxes[i].right > other.left && boxes[i].top < other.bottom && boxes[i].bottom > other.top;
          expect(overlaps).toBe(false);
        }
      }
    }
    page.once("dialog", (dialog) => dialog.dismiss());
    await page.getByRole("button", { name: `Remove ${nameB}`, exact: true }).click();
    await expect(page.getByRole("button", { name: `Remove ${nameB}`, exact: true })).toBeVisible();
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: `Remove ${nameB}`, exact: true }).click();
    await expect(page.getByText(`@${nameB} removed from friends.`, { exact: true })).toBeVisible();

    await pageB.getByLabel("Username", { exact: true }).fill("");
    const blocked = await send(pageB, nameA); createdIds.push(blocked);
    await refresh(page, "Incoming");
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("region", { name: "your connections." }).getByRole("button", { name: `Block ${nameB}`, exact: true }).click();
    await expect(page.getByRole("button", { name: `Unblock ${nameB}`, exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("button", { name: `Unblock ${nameB}`, exact: true })).toBeVisible();
    const denied = await apiB.post("friend-requests", { data: { username: nameA } });
    const absent = await apiB.post("friend-requests", { data: { username: "m61_missing_peer" } });
    expect(denied.status()).toBe(404);
    expect((await denied.json()).error).toEqual((await absent.json()).error);
    expect((await (await apiB.get("users/search", { params: { q: nameA } })).json()).data.items).toEqual([]);
    expect((await apiB.put(`blocks/${nameA}`)).status()).toBe(204);
    await page.getByRole("button", { name: `Unblock ${nameB}`, exact: true }).click();
    await expect(page.getByText(`@${nameB} unblocked.`, { exact: true })).toBeVisible();
    expect((await apiA.post("friend-requests", { data: { username: nameB } })).status()).toBe(404);
    await refresh(pageB, "Friends");
    await pageB.getByRole("button", { name: `Unblock ${nameA}`, exact: true }).click();
    await expect(pageB.getByText(`@${nameA} unblocked.`, { exact: true })).toBeVisible();
    const friends = await (await apiA.get("friends")).json();
    expect(friends.data.items).not.toContainEqual(expect.objectContaining({ username: nameB }));
    expect((await apiA.delete("blocks/m61_missing_peer")).status()).toBe(204);

    const search = await apiC.get("users/search", { params: { q: nameA.slice(0, 3) } });
    expect(search.status()).toBe(200);
    const items = (await search.json()).data.items;
    expect(items.length).toBeLessThanOrEqual(10);
    for (const item of items) expect(Object.keys(item).sort()).toEqual(["relationship", "username"]);
    let limited = false;
    for (let attempt = 0; attempt < 31; attempt++) {
      const response = await apiC.get("users/search", { params: { q: "!" } });
      if (response.status() === 429) {
        expect(Number(response.headers()["retry-after"])).toBeGreaterThan(0);
        limited = true; break;
      }
      expect(response.status()).toBe(400);
    }
    expect(limited).toBe(true);
  } finally {
    if (clients.length >= 2) {
      for (const id of createdIds) {
        for (const api of clients.slice(0, 2)) {
          const pending = await api.delete(`friend-requests/${id}`);
          if (pending.status() === 409) await api.delete(`friends/${id}`);
        }
      }
      if (nameA && nameB) {
        await clients[0].delete(`blocks/${nameB}`);
        await clients[1].delete(`blocks/${nameA}`);
      }
    }
    for (const api of clients) await api.dispose();
    await b.close(); await c.close();
  }
});

test("a stale search 429 still pauses the current query without automatic retry", async ({ page }) => {
  let releaseOld: (() => void) | undefined;
  let oldStarted = false;
  let currentCalls = 0;
  await installApiTargetGuard(page, env.expectedApiUrl);
  await page.route("**/api/v1/users/search?*", async (route) => {
    const query = new URL(route.request().url()).searchParams.get("q");
    if (query === "ali") {
      oldStarted = true;
      await new Promise<void>((resolve) => { releaseOld = resolve; });
      await route.fulfill({ status: 429, contentType: "application/json", headers: { "Retry-After": "2", "Access-Control-Expose-Headers": "Retry-After", "Access-Control-Allow-Origin": env.baseUrl }, body: JSON.stringify({ error: { code: "RATE_LIMITED", message: "Wait" } }) });
    } else {
      currentCalls++;
      await route.continue();
    }
  });
  await page.goto(env.baseUrl + "/friends");
  await page.getByLabel("Username", { exact: true }).fill("ali");
  await expect.poll(() => oldStarted).toBe(true);
  await page.getByLabel("Username", { exact: true }).fill("bob");
  releaseOld?.();
  await expect(page.getByText(/Search limit reached/)).toBeVisible();
  expect(currentCalls).toBe(0);
  await expect(page.getByRole("button", { name: "Retry search", exact: true })).toBeVisible();
  expect(currentCalls).toBe(0);
  await page.getByRole("button", { name: "Retry search", exact: true }).click();
  await expect.poll(() => currentCalls).toBe(1);
});
