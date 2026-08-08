import { expect, test } from "@playwright/test";
import { e2eEnvironment } from "./support/environment";
import {
  authenticatedUserId,
  openDashboard,
  openTracker,
  restoreActiveSession,
  startSession,
  stopActiveSessionIfPresent,
  stopSession,
  tryStopEntryAsCurrentUser,
  waitForRecordedSecond,
} from "./support/critical-path";

const userAConfigured = Boolean(e2eEnvironment.userAStorageState);
const twoUsersConfigured = Boolean(
  e2eEnvironment.userAStorageState && e2eEnvironment.userBStorageState,
);

function utcDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function expectSameDashboardDay(startDate: string): void {
  expect(utcDate(), "The scenario crossed a UTC dashboard boundary; rerun it.").toBe(startDate);
}

function expectSameInstant(actual: string, expected: string): void {
  // PostgreSQL stores timestamptz at microsecond precision while the create response
  // can contain Java nanoseconds; compare at the browser's millisecond precision.
  expect(new Date(actual).toISOString()).toBe(new Date(expected).toISOString());
}

test.describe("authenticated tracker critical path", () => {
  test.skip(
    !userAConfigured,
    "External User A auth is not configured; see e2e/README.md or set ROTRACK_E2E_REQUIRE_AUTH=1 to make this a configuration error.",
  );

  test.afterEach(async ({ page }) => {
    if (userAConfigured && !page.isClosed()) {
      await stopActiveSessionIfPresent(page).catch(() => undefined);
    }
  });

  test("Work survives reload and navigation, stops explicitly, and reaches dashboard totals", async ({
    page,
  }) => {
    await stopActiveSessionIfPresent(page);
    const dashboardDate = utcDate();
    const before = await openDashboard(page);
    await openTracker(page);

    const started = await startSession(page, "WORK");
    await waitForRecordedSecond(page);

    const restored = await restoreActiveSession(page);
    expect(restored.id).toBe(started.id);
    expectSameInstant(restored.startTime, started.startTime);

    await page.getByRole("link", { name: "View dashboard" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText("your last seven local days", { exact: true })).toBeVisible();

    await openTracker(page);
    await expect(page.getByRole("button", { name: "Stop session" })).toBeVisible();
    const stopped = await stopSession(page);
    expect(stopped.id).toBe(started.id);
    expectSameInstant(stopped.startTime, started.startTime);
    expect(stopped.durationSeconds).toBeGreaterThanOrEqual(1);

    const after = await openDashboard(page);
    expectSameDashboardDay(dashboardDate);
    expect(after.totalSeconds.WORK - before.totalSeconds.WORK).toBe(stopped.durationSeconds);
    expect(after.recentSessions.some((entry) => entry.id === stopped.id)).toBe(true);
  });

  test("Rot starts and stops explicitly and reaches the owner's dashboard totals", async ({ page }) => {
    await stopActiveSessionIfPresent(page);
    const dashboardDate = utcDate();
    const before = await openDashboard(page);
    await openTracker(page);

    const started = await startSession(page, "ROT");
    await waitForRecordedSecond(page);
    const stopped = await stopSession(page);

    expect(stopped.id).toBe(started.id);
    expect(stopped.activityType).toBe("ROT");
    expect(stopped.durationSeconds).toBeGreaterThanOrEqual(1);

    const after = await openDashboard(page);
    expectSameDashboardDay(dashboardDate);
    expect(after.totalSeconds.ROT - before.totalSeconds.ROT).toBe(stopped.durationSeconds);
    expect(after.recentSessions.some((entry) => entry.id === stopped.id)).toBe(true);
  });

  test("browser close and a new browser context restore the same active session", async ({ browser }) => {
    const firstContext = await browser.newContext({
      storageState: e2eEnvironment.userAStorageState,
    });
    const firstPage = await firstContext.newPage();
    await stopActiveSessionIfPresent(firstPage);
    const started = await startSession(firstPage, "WORK");
    await firstContext.close();

    const reopenedContext = await browser.newContext({
      storageState: e2eEnvironment.userAStorageState,
    });
    const reopenedPage = await reopenedContext.newPage();
    try {
      await openTracker(reopenedPage);
      const restored = await restoreActiveSession(reopenedPage);
      expect(restored.id).toBe(started.id);
      expectSameInstant(restored.startTime, started.startTime);
      expect(restored.endTime).toBeNull();
      await stopSession(reopenedPage);
    } finally {
      await stopActiveSessionIfPresent(reopenedPage).catch(() => undefined);
      await reopenedContext.close();
    }
  });
});

test.describe("two-user ownership isolation", () => {
  test.skip(
    !twoUsersConfigured,
    "User A and User B external auth are required; see e2e/README.md or set ROTRACK_E2E_REQUIRE_AUTH=1 to fail fast.",
  );

  test("User B cannot restore, stop, or aggregate User A's completed session", async ({ browser }) => {
    const userAContext = await browser.newContext({
      storageState: e2eEnvironment.userAStorageState,
    });
    const userBContext = await browser.newContext({
      storageState: e2eEnvironment.userBStorageState,
    });
    const userAPage = await userAContext.newPage();
    const userBPage = await userBContext.newPage();

    try {
      await stopActiveSessionIfPresent(userAPage);
      await stopActiveSessionIfPresent(userBPage);
      expect(await authenticatedUserId(userAPage)).not.toBe(await authenticatedUserId(userBPage));

      const dashboardDate = utcDate();
      const userBBefore = await openDashboard(userBPage);
      await openTracker(userAPage);
      const startedWorkByA = await startSession(userAPage, "WORK");
      await waitForRecordedSecond(userAPage);

      await openTracker(userBPage);
      await expect(userBPage.getByText("no active session", { exact: true })).toBeVisible();
      await expect(userBPage.getByRole("button", { name: "Stop session" })).toHaveCount(0);

      const forbiddenWorkStop = await tryStopEntryAsCurrentUser(
        userBPage,
        startedWorkByA.apiOrigin,
        startedWorkByA.id,
      );
      expect(forbiddenWorkStop).toEqual({ status: 404, code: "NOT_FOUND" });

      const stillActiveForA = await restoreActiveSession(userAPage);
      expect(stillActiveForA.id).toBe(startedWorkByA.id);
      expect((await stopSession(userAPage)).id).toBe(startedWorkByA.id);

      const startedRotByA = await startSession(userAPage, "ROT");
      await waitForRecordedSecond(userAPage);
      const forbiddenRotStop = await tryStopEntryAsCurrentUser(
        userBPage,
        startedRotByA.apiOrigin,
        startedRotByA.id,
      );
      expect(forbiddenRotStop).toEqual({ status: 404, code: "NOT_FOUND" });
      expect((await stopSession(userAPage)).id).toBe(startedRotByA.id);

      const userAStats = await openDashboard(userAPage);
      expect(userAStats.recentSessions.some((entry) => entry.id === startedWorkByA.id)).toBe(true);
      expect(userAStats.recentSessions.some((entry) => entry.id === startedRotByA.id)).toBe(true);

      const userBAfter = await openDashboard(userBPage);
      expectSameDashboardDay(dashboardDate);
      expect(userBAfter.totalSeconds).toEqual(userBBefore.totalSeconds);
      expect(userBAfter.daily).toEqual(userBBefore.daily);
      expect(userBAfter.recentSessions.some((entry) => entry.id === startedWorkByA.id)).toBe(false);
      expect(userBAfter.recentSessions.some((entry) => entry.id === startedRotByA.id)).toBe(false);
    } finally {
      await stopActiveSessionIfPresent(userAPage).catch(() => undefined);
      await stopActiveSessionIfPresent(userBPage).catch(() => undefined);
      await userAContext.close();
      await userBContext.close();
    }
  });
});
