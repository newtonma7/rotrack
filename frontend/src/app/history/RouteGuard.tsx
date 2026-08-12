"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

/** Keeps an unauthenticated visitor's requested history route for post-login return. */
export default function HistoryRouteGuard({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) router.replace(`/signin?returnTo=${encodeURIComponent(pathname)}`);
  }, [user, loading, pathname, router]);

  if (loading) return <div className="flex min-h-screen items-center justify-center bg-[var(--rt-cream)] text-[var(--rt-ink-muted)]" role="status">Loading…</div>;
  if (!user) return null;
  return <>{children}</>;
}
