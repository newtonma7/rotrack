"use client";

/**
 * Sign-out control — clears Supabase session from browser storage and redirects to sign-in.
 *
 * Layer: frontend component (auth UI).
 * Data flow: supabase.auth.signOut() → local session cleared → AuthProvider sees null user.
 */

import { useRouter } from "next/navigation";
import { supabase } from "@/lib/supabase/client";
import { Button } from "@/components/ui/button";

export function SignOutButton({
  className,
  redirectTo = "/signin",
}: {
  className?: string;
  redirectTo?: string;
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
      onClick={() => void handleSignOut()}
      className={className}
    >
      Sign out
    </Button>
  );
}
