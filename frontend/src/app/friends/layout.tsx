import type { Metadata } from "next";
import { createPageMetadata } from "@/lib/site";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";

export const metadata: Metadata = createPageMetadata({ title: "Friends", description: "Manage your friendships and requests.", path: "/friends", noIndex: true });

export default function FriendsLayout({ children }: { children: React.ReactNode }) {
  return <ProtectedRoute><ApplicationHeader />{children}</ProtectedRoute>;
}
