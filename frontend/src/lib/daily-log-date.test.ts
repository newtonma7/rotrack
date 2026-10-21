import { describe, expect, it } from "vitest";
import { addLocalDays, calendarRange, isCanonicalLocalDate, localDateInTimeZone, shiftLocalMonth } from "@/lib/daily-log-date";

describe("daily log local date helpers", () => {
  it("accepts canonical Gregorian dates from year 0001 through 9999 only", () => {
    expect(isCanonicalLocalDate("0001-01-01")).toBe(true);
    expect(isCanonicalLocalDate("9999-12-31")).toBe(true);
    for (const invalid of ["0000-01-01", "+2026-01-01", "2026-2-01", "2025-02-29", "10000-01-01"]) {
      expect(isCanonicalLocalDate(invalid)).toBe(false);
    }
  });

  it("adds dates across leap days and year boundaries without timezone drift", () => {
    expect(addLocalDays("2024-02-28", 1)).toBe("2024-02-29");
    expect(addLocalDays("2024-12-31", 1)).toBe("2025-01-01");
    expect(addLocalDays("0001-01-01", 1)).toBe("0001-01-02");
  });

  it("builds a complete six-week half-open calendar range", () => {
    expect(calendarRange("2026-08-01")).toEqual({ start: "2026-07-26", end: "2026-09-06" });
    expect(calendarRange("2026-01-01")).toEqual({ start: "2025-12-28", end: "2026-02-08" });
  });

  it("keeps calendar projections inside the supported date domain at both endpoints", () => {
    expect(calendarRange("0001-01-01")).toEqual({ start: "0001-01-01", end: "0001-02-12" });
    expect(calendarRange("9999-12-31")).toEqual({ start: "9999-11-20", end: "9999-12-31" });
    expect(addLocalDays("9999-11-20", 41)).toBe("9999-12-31");
  });

  it("moves by calendar months across year boundaries and clamps month-end dates", () => {
    expect(shiftLocalMonth("2026-01-01", 1)).toBe("2026-02-01");
    expect(shiftLocalMonth("2026-01-31", 1)).toBe("2026-02-28");
    expect(shiftLocalMonth("2026-01-01", -1)).toBe("2025-12-01");
  });

  it("derives today's label in the selected IANA timezone", () => {
    expect(localDateInTimeZone(new Date("2026-01-01T01:00:00Z"), "America/Los_Angeles")).toBe("2025-12-31");
  });
});
