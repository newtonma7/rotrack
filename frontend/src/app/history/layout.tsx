import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import HistoryRouteGuard from "./RouteGuard";

export const metadata: Metadata = createPageMetadata({
  title: "History",
  description: "Review and correct your private completed time entries.",
  path: "/history",
  noIndex: true,
});

export default function HistoryLayout({ children }: { children: React.ReactNode }) {
  return <HistoryRouteGuard>{children}</HistoryRouteGuard>;
}
