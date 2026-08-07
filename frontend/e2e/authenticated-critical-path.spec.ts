import { test, expect } from "@playwright/test";
import { existsSync } from "node:fs";

const storageState = process.env.ROTRACK_E2E_STORAGE_STATE;
const configured = Boolean(storageState && existsSync(storageState));

test.describe("authenticated tracker critical path", () => {
  test.skip(!configured, "Set ROTRACK_E2E_STORAGE_STATE to an explicitly prepared auth state.");

  test("starts, restores after reload, navigates, and explicitly stops Work", async ({ page }) => {
    await page.goto("/tracker");
    await expect(page.getByRole("heading", { name: "Active session" })).toBeVisible();

    await page.getByRole("button", { name: "Work" }).click();
    await expect(page.getByRole("button", { name: "Stop session" })).toBeVisible();

    await page.reload();
    await expect(page.getByRole("button", { name: "Stop session" })).toBeVisible();

    await page.getByRole("link", { name: /view dashboard/i }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText(/your last seven local days/i)).toBeVisible();

    await page.goto("/tracker");
    await page.getByRole("button", { name: "Stop session" }).click();
    await expect(page.getByText("no active session")).toBeVisible();
  });
});
