"use client";

/**
 * Client-side guard for the protected dashboard route. The API still enforces
 * authorization; this guard only prevents an unauthenticated UI flash.
 */

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

export default function DashboardRouteGuard({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace("/signin");
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="min-h-screen bg-[var(--rt-cream)] flex items-center justify-center text-[var(--rt-ink-muted)]">
        Loading...
      </div>
    );
  }

  if (!user) return null;
  return <>{children}</>;
}
