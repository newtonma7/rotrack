import { notFound } from "next/navigation";
import { DailyLogsExperience } from "@/components/daily-logs/DailyLogsExperience";
import { isCanonicalLocalDate } from "@/lib/daily-log-date";
import { isValidTimeZone } from "@/lib/timezone";

export default async function DailyLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ localDate: string }>;
  searchParams: Promise<{ timeZone?: string | string[] }>;
}) {
  const [{ localDate }, { timeZone }] = await Promise.all([params, searchParams]);
  if (!isCanonicalLocalDate(localDate)) notFound();
  const override = typeof timeZone === "string" && isValidTimeZone(timeZone) ? timeZone : undefined;
  return <DailyLogsExperience localDate={localDate} initialTimeZone={override} />;
}
