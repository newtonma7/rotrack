import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";

export const metadata: Metadata = createPageMetadata({ title: "Notes", description: "Private rich-text study notes.", path: "/notes", noIndex: true });

export default function NotesLayout({ children }: { children: React.ReactNode }) {
  return <ProtectedRoute>{children}</ProtectedRoute>;
}
