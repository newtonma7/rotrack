import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";

export const metadata: Metadata = createPageMetadata({ title: "Daily Logs", description: "Private daily study logs and reflections.", path: "/daily-logs", noIndex: true });

export default function DailyLogsLayout({ children }: { children: React.ReactNode }) {
  return <ProtectedRoute>{children}</ProtectedRoute>;
}
