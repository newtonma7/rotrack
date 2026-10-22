import { DailyLogsExperience } from "@/components/daily-logs/DailyLogsExperience";
import { isValidTimeZone } from "@/lib/timezone";

export default async function DailyLogsPage({
  searchParams,
}: {
  searchParams: Promise<{ timeZone?: string | string[] }>;
}) {
  const { timeZone } = await searchParams;
  const override = typeof timeZone === "string" && isValidTimeZone(timeZone) ? timeZone : undefined;
  return <DailyLogsExperience initialTimeZone={override} />;
}
