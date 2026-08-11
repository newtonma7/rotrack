import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import DashboardRouteGuard from "./RouteGuard";

export const metadata: Metadata = createPageMetadata({
  title: "Dashboard",
  description: "Review your private Work and Rot totals across the last seven local days.",
  path: "/dashboard",
  noIndex: true,
});

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <DashboardRouteGuard>{children}</DashboardRouteGuard>;
}
