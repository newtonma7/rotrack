const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

function utcDate(year: number, month: number, day: number): Date {
  const date = new Date(0);
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCFullYear(year, month - 1, day);
  return date;
}

export function isCanonicalLocalDate(value: string): boolean {
  const match = DATE_PATTERN.exec(value);
  if (!match) return false;
  const [, yearText, monthText, dayText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1) return false;
  const date = utcDate(year, month, day);
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function addLocalDays(localDate: string, days: number): string {
  if (!isCanonicalLocalDate(localDate) || !Number.isInteger(days)) throw new RangeError("Invalid local date arithmetic");
  const [year, month, day] = localDate.split("-").map(Number);
  const date = utcDate(year, month, day + days);
  const result = `${String(date.getUTCFullYear()).padStart(4, "0")}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
  if (!isCanonicalLocalDate(result)) throw new RangeError("Date is outside the supported calendar");
  return result;
}

export function calendarRange(selectedDate: string): { start: string; end: string } {
  if (!isCanonicalLocalDate(selectedDate)) throw new RangeError("Invalid local date");
  const [year, month, day] = selectedDate.split("-").map(Number);
  const weekday = utcDate(year, month, day).getUTCDay();
  let start: string;
  try {
    start = addLocalDays(selectedDate, -weekday);
  } catch {
    start = "0001-01-01";
  }
  if (start > "9999-11-20") start = "9999-11-20";
  let end: string;
  try {
    end = addLocalDays(start, 42);
  } catch {
    end = "9999-12-31";
  }
  return { start, end };
}

export function localDateInTimeZone(instant: Date, timeZone: string): string {
  const fields = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(instant);
  const value = (type: Intl.DateTimeFormatPartTypes) => fields.find((part) => part.type === type)?.value ?? "";
  return `${value("year").padStart(4, "0")}-${value("month")}-${value("day")}`;
}

export function shiftLocalMonth(localDate: string, months: number): string {
  if (!isCanonicalLocalDate(localDate) || !Number.isInteger(months)) throw new RangeError("Invalid month arithmetic");
  const [year, month, day] = localDate.split("-").map(Number);
  const absoluteMonth = (year - 1) * 12 + month - 1 + months;
  if (absoluteMonth < 0 || absoluteMonth >= 9999 * 12) throw new RangeError("Month is outside the supported calendar");
  const targetYear = Math.floor(absoluteMonth / 12) + 1;
  const targetMonth = absoluteMonth % 12 + 1;
  const lastDay = utcDate(targetYear, targetMonth + 1, 0).getUTCDate();
  return `${String(targetYear).padStart(4, "0")}-${String(targetMonth).padStart(2, "0")}-${String(Math.min(day, lastDay)).padStart(2, "0")}`;
}

export function monthStart(localDate: string): string {
  if (!isCanonicalLocalDate(localDate)) throw new RangeError("Invalid local date");
  return `${localDate.slice(0, 7)}-01`;
}

export function formatLocalDate(localDate: string, options: Intl.DateTimeFormatOptions = { month: "long", day: "numeric", year: "numeric" }): string {
  if (!isCanonicalLocalDate(localDate)) return localDate;
  const [year, month, day] = localDate.split("-").map(Number);
  return new Intl.DateTimeFormat(undefined, { ...options, timeZone: "UTC" }).format(utcDate(year, month, day));
}
