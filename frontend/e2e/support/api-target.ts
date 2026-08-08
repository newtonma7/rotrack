import type { Page } from "@playwright/test";

const API_PREFIX = "/api/v1";

export function isApplicationApiPath(candidateUrl: string): boolean {
  try {
    const pathname = new URL(candidateUrl).pathname;
    return new RegExp(`(?:^|/)${API_PREFIX.slice(1)}(?:/|$)`).test(pathname);
  } catch {
    return false;
  }
}

export function isExpectedApiUrl(candidateUrl: string, expectedApiUrl: string): boolean {
  try {
    const candidate = new URL(candidateUrl);
    const expected = new URL(expectedApiUrl);
    const expectedPath = expected.pathname.replace(/\/$/, "");
    return (
      candidate.origin === expected.origin &&
      (candidate.pathname === expectedPath || candidate.pathname.startsWith(`${expectedPath}/`))
    );
  } catch {
    return false;
  }
}

/** Block an API request before transmission if the frontend targets an unapproved API. */
export async function installApiTargetGuard(
  page: Page,
  expectedApiUrl: string | undefined,
): Promise<void> {
  if (!expectedApiUrl) return;
  await page.route("**/*", async (route) => {
    const requestUrl = route.request().url();
    if (isApplicationApiPath(requestUrl) && !isExpectedApiUrl(requestUrl, expectedApiUrl)) {
      await route.abort("blockedbyclient");
      return;
    }
    await route.continue();
  });
}
