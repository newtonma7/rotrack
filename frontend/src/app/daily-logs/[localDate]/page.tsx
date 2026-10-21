import { notFound } from "next/navigation";
import { DailyLogsExperience } from "@/components/daily-logs/DailyLogsExperience";
import { isCanonicalLocalDate } from "@/lib/daily-log-date";

export default async function DailyLogPage({ params }: { params: Promise<{ localDate: string }> }) {
  const { localDate } = await params;
  if (!isCanonicalLocalDate(localDate)) notFound();
  return <DailyLogsExperience localDate={localDate} />;
}
