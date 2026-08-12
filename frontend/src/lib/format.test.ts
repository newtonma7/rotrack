import { describe, expect, it } from "vitest";
import { formatDuration } from "@/lib/format";

describe("formatDuration", () => {
  it("keeps server-provided seconds visible in history and dashboard labels", () => {
    expect(formatDuration(61)).toBe("1m 1s");
    expect(formatDuration(3601)).toBe("1h 1s");
    expect(formatDuration(5400)).toBe("1h 30m");
  });
});
