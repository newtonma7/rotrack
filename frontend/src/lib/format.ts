export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  if (seconds < 60) return `${seconds}s`;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours === 0) return `${minutes}m`;
  return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`;
}

export function formatAxisDuration(totalSeconds: number): string {
  if (totalSeconds === 0) return "0";
  if (totalSeconds < 3600) return `${Math.round(totalSeconds / 60)}m`;
  const hours = totalSeconds / 3600;
  return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}h`;
}

export function formatLocalDate(localDate: string, format: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(undefined, { ...format, timeZone: "UTC" }).format(
    new Date(`${localDate}T00:00:00Z`),
  );
}

export function formatSessionDate(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone,
  }).format(new Date(instant));
}
