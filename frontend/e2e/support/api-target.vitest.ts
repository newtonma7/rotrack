import type { Page, Route } from "@playwright/test";
import { describe, expect, it, vi } from "vitest";
import { installApiTargetGuard, isApplicationApiPath, isExpectedApiUrl } from "./api-target";

const expected = "https://api.staging.example.test/api/v1";

describe("authenticated E2E API target guard", () => {
  it("accepts only the approved origin and API base", () => {
    expect(isExpectedApiUrl("https://api.staging.example.test/api/v1/time-entries/start", expected)).toBe(true);
    expect(isExpectedApiUrl("https://api.staging.example.test/api/v1", expected)).toBe(true);
  });

  it.each([
    "https://api.production.example.test/api/v1/time-entries/start",
    "https://api.staging.example.test/api/v10/time-entries/start",
    "https://api.staging.example.test/other/api/v1/time-entries/start",
    "not-a-url",
  ])("rejects an unapproved target: %s", (candidate) => {
    expect(isExpectedApiUrl(candidate, expected)).toBe(false);
  });

  it("recognizes only the application API path for pre-transmission blocking", () => {
    expect(isApplicationApiPath("https://other.example.test/api/v1/time-entries/start")).toBe(true);
    expect(isApplicationApiPath("https://other.example.test/api/v10/time-entries/start")).toBe(false);
  });

  it.each([
    "https://api.production.example.test/api/v1/time-entries/start",
    "https://api.production.example.test/other/api/v1/time-entries/start",
  ])("aborts a mismatched API request before it can continue: %s", async (candidate) => {
    let handler: ((route: Route) => Promise<void>) | undefined;
    const page = {
      route: vi.fn(async (_pattern: string, candidateHandler: (route: Route) => Promise<void>) => {
        handler = candidateHandler;
      }),
    } as unknown as Page;
    const abort = vi.fn(async () => undefined);
    const continueRequest = vi.fn(async () => undefined);
    const route = {
      request: () => ({ url: () => candidate }),
      abort,
      continue: continueRequest,
    } as unknown as Route;

    await installApiTargetGuard(page, expected);
    expect(handler).toBeDefined();
    await handler!(route);

    expect(abort).toHaveBeenCalledWith("blockedbyclient");
    expect(continueRequest).not.toHaveBeenCalled();
  });
});
