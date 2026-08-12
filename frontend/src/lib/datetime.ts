import { getBrowserTimeZone } from "@/lib/timezone";

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function localParts(instant: Date, timeZone: string): Record<string, number> {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    calendar: "gregory",
    numberingSystem: "latn",
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(instant);
  return Object.fromEntries(
    parts.filter(({ type }) => type !== "literal").map(({ type, value }) => [type, Number(value)]),
  );
}

function formatLocal(instant: Date, timeZone: string): string {
  const parts = localParts(instant, timeZone);
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}T${pad(parts.hour)}:${pad(parts.minute)}:${pad(parts.second)}`;
}

function offsetMilliseconds(instant: Date, timeZone: string): number {
  const parts = localParts(instant, timeZone);
  return Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second)
    - instant.getTime();
}

/** Native datetime-local values are wall-clock values in the selected IANA zone. */
export function toIsoInstant(value: string, timeZone = getBrowserTimeZone()): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
  if (!match) throw new Error("Enter a valid date and time.");

  const [, yearText, monthText, dayText, hourText, minuteText, secondText = "00"] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const wallClock = Date.UTC(year, month - 1, day, hour, minute, second);
  if (
    !Number.isFinite(wallClock)
    || new Date(wallClock).toISOString().slice(0, 19) !== `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}:${secondText}`
    || hour > 23 || minute > 59 || second > 59
  ) {
    throw new Error("Enter a valid date and time.");
  }

  // Two iterations cover the normal/DST offset change; the final format check
  // rejects nonexistent spring-forward wall times instead of silently shifting them.
  let instant = wallClock;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    instant = wallClock - offsetMilliseconds(new Date(instant), timeZone);
  }
  const result = new Date(instant);
  if (formatLocal(result, timeZone) !== `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}:${secondText}`) {
    throw new Error("Enter a valid date and time.");
  }
  return result.toISOString();
}

export function toDateTimeLocal(isoInstant: string, timeZone = getBrowserTimeZone()): string {
  const date = new Date(isoInstant);
  if (Number.isNaN(date.getTime())) throw new Error("Enter a valid date and time.");
  return formatLocal(date, timeZone);
}
