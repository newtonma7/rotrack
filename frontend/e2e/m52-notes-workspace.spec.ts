import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { installApiTargetGuard } from "./support/api-target";
import { e2eEnvironment } from "./support/environment";

const configured = Boolean(
  e2eEnvironment.userAStorageState &&
  e2eEnvironment.userBStorageState &&
  e2eEnvironment.expectedApiUrl,
);

test.describe("M5.2 Notes workspace", () => {
  test.skip(!configured, "External disposable auth state and the approved local API are required.");

  test("autosaves, preserves conflicts, isolates owners, and uses mobile detail navigation", async ({ browser, page }) => {
    const title = `m52-${Date.now()}`;
    let secondContext: BrowserContext | undefined;
    let userBContext: BrowserContext | undefined;
    let originalUrl: string | undefined;
    let copiedUrl: string | undefined;

    try {
      await installApiTargetGuard(page, e2eEnvironment.expectedApiUrl);
      await page.goto("/notes");
      await expect(page.getByRole("heading", { name: "notes." })).toBeVisible();
      await page.getByLabel("Note title").fill(title);
      await page.getByLabel("Note content").fill("private browser acceptance body");
      await expect(page.getByRole("status", { name: "" }).filter({ hasText: "Saved" })).toBeVisible();

      await page.getByRole("button", { name: new RegExp(title) }).click();
      await expect(page).toHaveURL(/\/notes\/[0-9a-f-]+$/);
      originalUrl = page.url();

      secondContext = await browser.newContext({ storageState: e2eEnvironment.userAStorageState! });
      const secondPage = await secondContext.newPage();
      await installApiTargetGuard(secondPage, e2eEnvironment.expectedApiUrl);
      await secondPage.goto(originalUrl);
      await expect(secondPage.getByLabel("Note title")).toHaveValue(title);

      await page.getByLabel("Note title").fill(`${title}-server`);
      await expect(page.getByRole("status", { name: "" }).filter({ hasText: "Saved" })).toBeVisible();
      await secondPage.getByLabel("Note title").fill(`${title}-local`);
      await expect(secondPage.getByRole("status", { name: "" }).filter({ hasText: "Conflict" })).toBeVisible();
      await expect(secondPage.getByLabel("Note title")).toHaveValue(`${title}-local`);

      await secondPage.getByRole("button", { name: "Save as new Note" }).click();
      await expect(secondPage.getByRole("status", { name: "" }).filter({ hasText: "Saved" })).toBeVisible();
      await expect(secondPage).not.toHaveURL(originalUrl);
      copiedUrl = secondPage.url();

      userBContext = await browser.newContext({ storageState: e2eEnvironment.userBStorageState! });
      const userBPage = await userBContext.newPage();
      await installApiTargetGuard(userBPage, e2eEnvironment.expectedApiUrl);
      await userBPage.goto(originalUrl);
      await expect(userBPage.getByRole("alert")).toBeVisible();
      await expect(userBPage.getByLabel("Note title")).toHaveCount(0);

      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(originalUrl);
      await expect(page.getByRole("region", { name: "Note editor" })).toBeVisible();
      await expect(page.getByRole("complementary", { name: "Notes library" })).toBeHidden();
      await expect(page.getByRole("button", { name: "Back to notes" })).toBeVisible();
    } finally {
      if (copiedUrl && secondContext) await deleteThroughUi(secondContext.pages()[0], copiedUrl);
      if (originalUrl) await deleteThroughUi(page, originalUrl);
      await userBContext?.close();
      await secondContext?.close();
    }
  });
});

async function deleteThroughUi(page: Page, url: string): Promise<void> {
  try {
    await page.goto(url);
    const button = page.getByRole("button", { name: "Delete note" });
    if (await button.isVisible()) {
      page.once("dialog", (dialog) => dialog.accept());
      await button.click();
      await expect(page).toHaveURL(/\/notes$/);
    }
  } catch {
    // The disposable local database is discarded after acceptance; preserve the primary failure.
  }
}
