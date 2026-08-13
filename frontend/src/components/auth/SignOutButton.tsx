"use client";

/**
 * Sign-out control — clears Supabase session from browser storage and redirects to sign-in.
 *
 * Layer: frontend component (auth UI).
 * Data flow: supabase.auth.signOut() → local session cleared → AuthProvider sees null user.
 */

import type { ReactNode } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "@/lib/supabase/client";
import { requestAppNavigation } from "@/lib/navigation-guard";
import { Button } from "@/components/ui/button";

export function SignOutButton({
  className,
  redirectTo = "/signin",
  children = "Sign out",
  "aria-label": ariaLabel,
}: {
  className?: string;
  redirectTo?: string;
  children?: ReactNode;
  "aria-label"?: string;
}) {
  const router = useRouter();

  const handleSignOut = async () => {
    await supabase.auth.signOut();
    router.push(redirectTo);
  };

  return (
    <Button
      type="button"
      variant="ghost"
      onClick={() => requestAppNavigation(() => void handleSignOut())}
      aria-label={ariaLabel}
      className={className}
    >
      {children}
    </Button>
  );
}
