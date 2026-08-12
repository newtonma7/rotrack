import { describe, expect, it } from "vitest";
import { toDateTimeLocal, toIsoInstant } from "@/lib/datetime";

describe("datetime-local conversion", () => {
  it("round-trips a local control value through an ISO instant", () => {
    const localValue = "2026-08-12T14:30";
    expect(toDateTimeLocal(toIsoInstant(localValue))).toBe(localValue);
  });

  it("rejects malformed or impossible local values", () => {
    expect(() => toIsoInstant("")).toThrow("Enter a valid date and time.");
    expect(() => toIsoInstant("2026-02-30T14:30")).toThrow("Enter a valid date and time.");
  });
});
