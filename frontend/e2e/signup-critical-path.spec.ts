import { expect, test, type Page } from "@playwright/test";
import { e2eEnvironment } from "./support/environment";

const signupConfigured = Boolean(
  e2eEnvironment.signupEmailA &&
    e2eEnvironment.signupEmailB &&
    e2eEnvironment.signupPassword,
);

function signupUsername(): string {
  return e2eEnvironment.signupUsername ?? `e2e_${Date.now().toString(36)}`;
}

async function fillSignup(
  page: Page,
  email: string,
  username: string,
): Promise<void> {
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(e2eEnvironment.signupPassword!);
  await page.getByLabel("Confirm password").fill(e2eEnvironment.signupPassword!);
}

test.describe.serial("disposable username signup", () => {
  test.skip(
    !signupConfigured,
    "Disposable signup emails are not configured; set ROTRACK_E2E_REQUIRE_SIGNUP=1 to make this a configuration error.",
  );

  test("requires a username, normalizes it, and reserves it before confirmation", async ({ page }) => {
    const username = signupUsername();
    await page.goto("/signup");
    await page.getByRole("button", { name: "Sign up" }).click();
    await expect(page.getByRole("alert")).toContainText("Username is required");

    await fillSignup(page, e2eEnvironment.signupEmailA!, `  ${username.toUpperCase()}  `);
    await page.getByRole("button", { name: "Sign up" }).click();
    await expect(page).toHaveURL(/\/signup\/confirmation$/);

    await page.goto("/signup");
    await fillSignup(page, e2eEnvironment.signupEmailB!, username);
    await page.getByRole("button", { name: "Sign up" }).click();
    await expect(page.getByRole("alert")).toHaveText("That username is unavailable. Try another one.");
  });

  test("confirms the disposable account and signs in", async ({ page }) => {
    test.skip(
      !e2eEnvironment.signupConfirmationUrl,
      "A disposable confirmation URL is not configured; set ROTRACK_E2E_REQUIRE_SIGNUP_CONFIRMATION=1 to make this a configuration error.",
    );

    await page.goto(e2eEnvironment.signupConfirmationUrl!);
    await page.goto("/signin");
    await page.getByLabel("Email").fill(e2eEnvironment.signupEmailA!);
    await page.getByLabel("Password").fill(e2eEnvironment.signupPassword!);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText("your last seven local days", { exact: true })).toBeVisible();
  });
});
