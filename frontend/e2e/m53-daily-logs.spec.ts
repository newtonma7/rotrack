import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from "@playwright/test";
import { currentSupabaseAuth } from "./support/critical-path";
import { installApiTargetGuard } from "./support/api-target";
import { e2eEnvironment } from "./support/environment";

const configured = Boolean(e2eEnvironment.userAStorageState && e2eEnvironment.userBStorageState && e2eEnvironment.expectedApiUrl);
const localHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);
test.skip(!configured && !e2eEnvironment.requireAuth, "M5.3 requires two external local storage states and the disposable local API.");
test.skip(!configured, "ROTRACK_E2E_REQUIRE_AUTH=1 requires both user states and an approved API target.");

type Result = { status: number; body: unknown };
type Reflection = { contentText: string; version: number; timeZoneAtCreation: string };
type DailyLog = {
  reflection: Reflection | null;
  localDate: string;
  timeZone: string;
  generated: { sessions: Array<{ id: string; segmentStartTime: string; segmentEndTime: string; durationSeconds: number }> };
};
const payload = (text: string, expectedVersion: number) => ({ contentJson: { schemaVersion: 1, document: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] } }, expectedVersion });
const randomYear = () => String(2100 + Math.floor(Math.random() * 7000));

async function auth(page: Page): Promise<string> {
  await page.goto(e2eEnvironment.baseUrl);
  const session = await currentSupabaseAuth(page);
  return session.accessToken;
}

async function api(token: string): Promise<APIRequestContext> {
  return playwrightRequest.newContext({ baseURL: e2eEnvironment.expectedApiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

async function call(context: APIRequestContext, method: string, path: string, data?: unknown, headers: Record<string, string> = {}): Promise<Result> {
  const url = new URL(path.replace(/^\/+/, ""), `${e2eEnvironment.expectedApiUrl!}/`).toString();
  const response = await context.fetch(url, { method, ...(data === undefined ? {} : { data }), headers });
  const raw = await response.text();
  return { status: response.status(), body: raw ? JSON.parse(raw) as unknown : null };
}

function data<T>(result: Result): T { return (result.body as { data: T }).data; }

test.describe("M5.3 private Daily Logs and Reflection", () => {
  test("date detail, timezone projection, idempotent Reflection create, version conflict, and two-user privacy", async ({ page, browser }) => {
    test.skip(!e2eEnvironment.expectedApiUrl || !localHosts.has(new URL(e2eEnvironment.baseUrl).hostname) || !localHosts.has(new URL(e2eEnvironment.expectedApiUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    const tokenA = await auth(page);
    const apiA = await api(tokenA);
    const oversized = await call(apiA, "PUT", "/daily-logs/not-a-date/reflection", {
      contentJson: {}, expectedVersion: 0, padding: "x".repeat(1_048_576),
    });
    expect(oversized.status).toBe(413);
    expect(JSON.stringify(oversized.body)).not.toContain("padding");
    const date = `${randomYear()}-11-17`;
    const dstEntry = await call(apiA, "POST", "/time-entries", {
      activityType: "WORK",
      startTime: "2026-03-08T06:30:00Z",
      endTime: "2026-03-08T07:30:00Z",
      notes: null,
    });
    expect(dstEntry.status).toBe(201);
    const dstEntryId = data<{ id: string }>(dstEntry).id;
    try {
      const range = await call(apiA, "GET", "/daily-logs?start=2026-03-07&end=2026-03-09&timeZone=America%2FNew_York");
      expect(range.status).toBe(200);
      const summary = data<{ range: { start: string; end: string }; days: Array<{ localDate: string; workSeconds: number; sessionCount: number }> }>(range);
      expect(summary.days.map((day) => day.localDate)).toEqual(["2026-03-07", "2026-03-08"]);
      expect(summary.days[1].workSeconds).toBeGreaterThanOrEqual(3600);
      expect(summary.days[1].sessionCount).toBeGreaterThanOrEqual(1);
      expect(JSON.stringify(summary)).not.toContain("sessionLabel");
      const dstDetail = data<DailyLog>(await call(apiA, "GET", "/daily-logs/2026-03-08?timeZone=America%2FNew_York"));
      expect(dstDetail.generated.sessions).toContainEqual(expect.objectContaining({
        id: dstEntryId,
        segmentStartTime: "2026-03-08T06:30:00Z",
        segmentEndTime: "2026-03-08T07:30:00Z",
        durationSeconds: 3600,
      }));
    } finally {
      expect((await call(apiA, "DELETE", `/time-entries/${dstEntryId}`)).status).toBe(204);
    }

    const first = await call(apiA, "GET", `/daily-logs/${date}?timeZone=America%2FNew_York`);
    expect(first.status).toBe(200);
    expect(data<DailyLog>(first).reflection).toBeNull();
    const key = crypto.randomUUID();
    const request = payload("local-only reflection", 0);
    const created = await call(apiA, "PUT", `/daily-logs/${date}/reflection?timeZone=America%2FNew_York`, request, { "Idempotency-Key": key });
    expect(created.status).toBe(200);
    expect(data<Reflection>(created).contentText).toBe("local-only reflection");
    const replay = await call(apiA, "PUT", `/daily-logs/${date}/reflection?timeZone=America%2FNew_York`, request, { "Idempotency-Key": key });
    expect(replay.status).toBe(200);
    expect(data<Reflection>(replay)).toEqual(data<Reflection>(created));
    expect((await call(apiA, "PUT", `/daily-logs/${date}/reflection?timeZone=UTC`, payload("updated reflection", 1))).status).toBe(200);
    const stale = await call(apiA, "PUT", `/daily-logs/${date}/reflection?timeZone=UTC`, payload("must not overwrite", 1));
    expect(stale.status).toBe(409);
    expect(JSON.stringify(stale.body)).not.toContain("updated reflection");
    const reprojected = data<DailyLog>(await call(apiA, "GET", `/daily-logs/${date}?timeZone=UTC`));
    expect(reprojected.reflection?.timeZoneAtCreation).toBe("America/New_York");

    const contextB = await browser.newContext({ storageState: e2eEnvironment.userBStorageState });
    const pageB = await contextB.newPage();
    try {
      const tokenB = await auth(pageB);
      const apiB = await api(tokenB);
      const other = data<DailyLog>(await call(apiB, "GET", `/daily-logs/${date}?timeZone=UTC`));
      expect(other.reflection).toBeNull();
      await apiB.dispose();
    } finally { await contextB.close(); await apiA.dispose(); }
  });

  test("clearing after a lost creation response clears the persisted Reflection", async ({ page }) => {
    test.skip(!localHosts.has(new URL(e2eEnvironment.baseUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    const date = `${randomYear()}-11-20`;
    let dropFirstResponse = true;
    await page.route(`**/api/v1/daily-logs/${date}/reflection?*`, async (route) => {
      const response = await route.fetch();
      if (dropFirstResponse) {
        dropFirstResponse = false;
        expect(response.status()).toBe(200);
        await route.abort();
      } else {
        await route.fulfill({ response });
      }
    });
    await page.goto(`${e2eEnvironment.baseUrl}/daily-logs/${date}?timeZone=UTC`);
    const editor = page.locator('[contenteditable="true"][aria-label="Reflection content"]');
    await editor.fill("creation response was lost");
    await expect(page.getByRole("status").filter({ hasText: /^Offline$/ })).toBeVisible();
    await editor.fill("");
    await expect(page.getByRole("status").filter({ hasText: /^(Saved|Draft)$/ })).toBeVisible();
    await page.reload();
    await expect(editor).toBeEmpty();
  });

  test("cross-tab Reflection conflict preserves local edits until confirmed reload", async ({ page, browser }) => {
    test.skip(!localHosts.has(new URL(e2eEnvironment.baseUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    const apiA = await api(await auth(page));
    const date = `${randomYear()}-11-19`;
    const created = await call(apiA, "PUT", `/daily-logs/${date}/reflection?timeZone=UTC`, payload("shared starting version", 0), { "Idempotency-Key": crypto.randomUUID() });
    expect(created.status).toBe(200);
    const otherContext = await browser.newContext({ storageState: e2eEnvironment.userAStorageState });
    const otherPage = await otherContext.newPage();
    try {
      await installApiTargetGuard(otherPage, e2eEnvironment.expectedApiUrl);
      const url = `${e2eEnvironment.baseUrl}/daily-logs/${date}?timeZone=UTC`;
      await Promise.all([page.goto(url), otherPage.goto(url)]);
      const firstEditor = page.locator('[contenteditable="true"][aria-label="Reflection content"]');
      const secondEditor = otherPage.locator('[contenteditable="true"][aria-label="Reflection content"]');
      await expect(firstEditor).toContainText("shared starting version");
      await expect(secondEditor).toContainText("shared starting version");
      await firstEditor.fill("first tab saved version");
      await expect(page.getByRole("status").filter({ hasText: /^Saved$/ })).toBeVisible();
      await secondEditor.fill("second tab unsaved version");
      await expect(otherPage.getByRole("status").filter({ hasText: /^Conflict$/ })).toBeVisible();
      await expect(secondEditor).toContainText("second tab unsaved version");
      await expect(otherPage.getByRole("button", { name: "Copy my edits" })).toBeVisible();
      otherPage.once("dialog", (dialog) => dialog.accept());
      await otherPage.getByRole("button", { name: "Reload server version" }).click();
      await expect(secondEditor).toContainText("first tab saved version");
      await expect(secondEditor).not.toContainText("second tab unsaved version");
    } finally {
      await otherContext.close();
      await apiA.dispose();
    }
  });

  test("timezone override survives calendar and detail navigation", async ({ page }) => {
    test.skip(!localHosts.has(new URL(e2eEnvironment.baseUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    await page.goto(`${e2eEnvironment.baseUrl}/daily-logs`);
    await expect(page.getByRole("heading", { name: "reflection", exact: true })).toBeVisible();
    await page.getByRole("combobox", { name: "Timezone" }).selectOption("Asia/Tokyo");
    await page.getByRole("gridcell").nth(10).getByRole("button").click();
    await expect(page).toHaveURL(/\/daily-logs\/\d{4}-\d{2}-\d{2}/);
    await expect(page.getByRole("combobox", { name: "Timezone" })).toHaveValue("Asia/Tokyo");
    await page.getByRole("link", { name: "← calendar" }).click();
    await expect(page.getByRole("combobox", { name: "Timezone" })).toHaveValue("Asia/Tokyo");
  });

  test("phone calendar exposes loading failures and a retry action", async ({ page }) => {
    test.skip(!localHosts.has(new URL(e2eEnvironment.baseUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await page.setViewportSize({ width: 390, height: 844 });
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    await page.route("**/api/v1/daily-logs**", (route) => route.abort());
    await page.goto(`${e2eEnvironment.baseUrl}/daily-logs`);
    await expect(page.getByRole("alert").filter({ hasText: "Daily logs could not be loaded." })).toBeVisible();
    await expect(page.getByRole("button", { name: "Retry", exact: true })).toBeVisible();
  });

  test("calendar is keyboard operable and reflection autosaves then reloads", async ({ page }) => {
    test.skip(!localHosts.has(new URL(e2eEnvironment.baseUrl).hostname), "M5.3 acceptance is restricted to loopback targets.");
    await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
    const date = `${randomYear()}-11-18`;
    await page.goto(`${e2eEnvironment.baseUrl}/daily-logs/${date}`);
    await expect(page.getByRole("heading", { name: "daily logs." })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Application" })).toBeVisible();
    await expect(page.getByRole("link", { name: "dashboard", exact: true })).toBeVisible();
    await expect(page.getByRole("grid", { name: /daily logs/i })).toBeVisible();
    await expect(page.getByLabel("Reflection content")).toBeVisible();
    await expect(page.getByRole("status")).toContainText(/Draft|Saved|Waiting|Saving/);
    await expect(page.getByRole("link", { name: "← calendar" })).toBeVisible();
    const previousMonth = page.getByRole("button", { name: "Previous month" });
    await expect(previousMonth).toBeEnabled();
    await expect(page.getByRole("button", { name: "Next month" })).toBeEnabled();
    await previousMonth.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: `October ${date.slice(0, 4)}` })).toBeVisible();
    await page.getByRole("button", { name: "Next month" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: `November ${date.slice(0, 4)}` })).toBeVisible();
    const reflection = page.locator('[contenteditable="true"][aria-label="Reflection content"]');
    await reflection.fill("browser autosave acceptance");
    await expect(page.getByRole("status").filter({ hasText: "Saved" })).toBeVisible({ timeout: 10_000 });
    await page.reload();
    await expect(page.locator('[contenteditable="true"][aria-label="Reflection content"]')).toContainText("browser autosave acceptance");
    await page.setViewportSize({ width: 390, height: 844 });
    const logoBounds = await page.getByRole("banner").getByRole("link", { name: "rotrack." }).boundingBox();
    const navigationBounds = await page.getByRole("navigation", { name: "Application" }).boundingBox();
    expect(logoBounds).not.toBeNull();
    expect(navigationBounds).not.toBeNull();
    if (logoBounds && navigationBounds) {
      expect(navigationBounds.y).toBeGreaterThanOrEqual(logoBounds.y + logoBounds.height);
      expect(navigationBounds.x).toBeGreaterThanOrEqual(0);
      expect(navigationBounds.x + navigationBounds.width).toBeLessThanOrEqual(390);
    }
    await expect(page.getByLabel("Daily log calendar")).toBeHidden();
    await expect(page.getByLabel(`Daily log for ${date}`)).toBeVisible();
  });
});
