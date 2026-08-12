import { describe, expect, it } from "vitest";
import { toDateTimeLocal, toIsoInstant } from "@/lib/datetime";

describe("datetime-local conversion", () => {
  it("round-trips seconds in a saved timezone", () => {
    const localValue = "2026-08-12T14:30:17";
    const instant = toIsoInstant(localValue, "America/New_York");
    expect(toDateTimeLocal(instant, "America/New_York")).toBe(localValue);
  });

  it("round-trips both sides of a DST transition", () => {
    expect(toDateTimeLocal(
      toIsoInstant("2026-03-08T01:30:01", "America/New_York"),
      "America/New_York",
    )).toBe("2026-03-08T01:30:01");
    expect(toDateTimeLocal(
      toIsoInstant("2026-11-01T01:30:01", "America/New_York"),
      "America/New_York",
    )).toBe("2026-11-01T01:30:01");
  });

  it("rejects malformed or impossible local values", () => {
    expect(() => toIsoInstant("")).toThrow("Enter a valid date and time.");
    expect(() => toIsoInstant("2026-02-30T14:30", "America/New_York")).toThrow("Enter a valid date and time.");
    expect(() => toIsoInstant("2026-03-08T02:30:00", "America/New_York")).toThrow("Enter a valid date and time.");
  });
});
