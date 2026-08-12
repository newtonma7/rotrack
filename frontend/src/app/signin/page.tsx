"use client";

import { Suspense } from "react";
import SignIn from "../../components/auth/SignIn";

export default function SignInPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center bg-[var(--rt-cream)]" role="status">Loading…</div>}>
      <SignIn />
    </Suspense>
  );
}
