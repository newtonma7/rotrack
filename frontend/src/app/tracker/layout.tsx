import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import TrackerRouteGuard from "./RouteGuard";

export const metadata: Metadata = createPageMetadata({
  title: "Tracker",
  description: "Explicitly track a Work or Rot session until you stop it.",
  path: "/tracker",
  noIndex: true,
});

export default function TrackerLayout({ children }: { children: React.ReactNode }) {
  return <TrackerRouteGuard>{children}</TrackerRouteGuard>;
}
