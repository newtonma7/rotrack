import { defineConfig, devices } from "@playwright/test";
import { e2eEnvironment } from "./e2e/support/environment";

export default defineConfig({
  testDir: "./e2e",
  outputDir: "./test-results",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: e2eEnvironment.baseUrl,
    storageState: e2eEnvironment.userAStorageState,
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
    timezoneId: "UTC",
    // Authenticated network traces can retain bearer headers. Prefer screenshots and server logs.
    trace: "off",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
