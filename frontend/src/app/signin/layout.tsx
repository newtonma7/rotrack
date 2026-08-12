import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";

export const metadata: Metadata = createPageMetadata({
  title: "Sign in",
  description: "Sign in to rotrack and pick up where you left off.",
  path: "/signin",
  noIndex: true,
});

export default function SignInLayout({ children }: { children: React.ReactNode }) {
  return children;
}
