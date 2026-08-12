import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";

export const metadata: Metadata = createPageMetadata({
  title: "Check your email",
  description: "Confirm your rotrack account before signing in.",
  path: "/signup/confirmation",
  noIndex: true,
});

export default function ConfirmationLayout({ children }: { children: React.ReactNode }) {
  return children;
}
