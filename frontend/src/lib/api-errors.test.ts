import { describe, expect, it } from "vitest";
import { parseApiErrorResponse } from "./api-errors";

describe("parseApiErrorResponse", () => {
  it("preserves structured backend error details", () => {
    const error = parseApiErrorResponse({
      error: {
        code: "VALIDATION_ERROR",
        message: "Request validation failed",
        fieldErrors: { activityType: "must not be null" },
      },
      timestamp: "2026-01-01T00:00:00Z",
      path: "/api/v1/time-entries/start",
    }, 400);

    expect(error).toMatchObject({
      name: "ApiRequestError",
      code: "VALIDATION_ERROR",
      status: 400,
      fieldErrors: { activityType: "must not be null" },
      message: "Request validation failed",
    });
  });
});
