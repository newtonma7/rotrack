"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";

/** Keeps the settings shell from flashing before the browser auth session resolves. */
export default function SettingsRouteGuard({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/signin");
  }, [user, loading, router]);

  if (loading) {
    return <div className="flex min-h-screen items-center justify-center bg-[var(--rt-cream)] text-[var(--rt-ink-muted)]" role="status">Loading…</div>;
  }
  if (!user) return null;
  return <>{children}</>;
}
