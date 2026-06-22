"use client";

/**
 * Route guard for /tracker — same pattern as dashboard/layout.tsx.
 * User must be signed in so api.ts can attach a Bearer token to Spring Boot requests.
 */

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

export default function TrackerLayout({
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
