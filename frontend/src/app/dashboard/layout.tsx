"use client";

/**
 * Route guard for /dashboard — requires a Supabase session.
 *
 * Landing (/) stays public; only app routes like dashboard and tracker are gated.
 * Uses client-side AuthProvider because we have not adopted @supabase/ssr yet.
 * Flow: no user after load → redirect to /signin (JWT needed for API calls anyway).
 */

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
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

  if (!user) {
    return null;
  }

  return <>{children}</>;
}
