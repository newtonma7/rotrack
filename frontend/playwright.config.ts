import { defineConfig, devices } from "@playwright/test";
import { existsSync } from "node:fs";

const storageState = process.env.ROTRACK_E2E_STORAGE_STATE;

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.ROTRACK_E2E_BASE_URL ?? "http://localhost:3000",
    storageState: storageState && existsSync(storageState) ? storageState : undefined,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
