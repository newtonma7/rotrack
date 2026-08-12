import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";

export const metadata: Metadata = createPageMetadata({
  title: "Sign up",
  description: "Create a rotrack account and start tracking Work or Rot.",
  path: "/signup",
  noIndex: true,
});

export default function SignUpLayout({ children }: { children: React.ReactNode }) {
  return children;
}
