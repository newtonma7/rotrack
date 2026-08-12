import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import SettingsRouteGuard from "./RouteGuard";

export const metadata: Metadata = createPageMetadata({
  title: "Settings",
  description: "Set your private timezone, daily Work goal, and sharing defaults.",
  path: "/settings",
  noIndex: true,
});

export default function SettingsLayout({ children }: { children: React.ReactNode }) {
  return <SettingsRouteGuard>{children}</SettingsRouteGuard>;
}
