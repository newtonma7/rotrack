import { describe, expect, it } from "vitest";
import { isValidTimeZone } from "@/lib/timezone";

describe("timezone validation", () => {
  it("accepts UTC and slash-form IANA zones", () => {
    for (const timeZone of ["UTC", "America/New_York", "Australia/Lord_Howe"]) {
      expect(isValidTimeZone(timeZone)).toBe(true);
    }
  });

  it("rejects JavaScript timezone aliases outside migration 004's set", () => {
    for (const timeZone of ["GMT", "UCT", "Zulu"]) {
      expect(isValidTimeZone(timeZone)).toBe(false);
    }
  });
});
