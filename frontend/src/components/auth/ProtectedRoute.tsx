"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

/** UI guard only; the API remains the authorization boundary. */
export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) {
      router.replace(`/signin?returnTo=${encodeURIComponent(pathname)}`);
    }
  }, [loading, pathname, router, user]);

  if (loading) {
    return <div className="flex min-h-screen items-center justify-center bg-[var(--rt-cream)] text-[var(--rt-ink-muted)]" role="status">Loading…</div>;
  }
  if (!user) return null;
  return <>{children}</>;
}
