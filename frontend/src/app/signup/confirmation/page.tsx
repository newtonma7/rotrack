"use client";

/**
 * Post-signup confirmation splash — shown after Supabase signUp succeeds.
 *
 * Layer: frontend route (auth UX). Supabase sends the confirmation email;
 * this page does not poll auth state — user must click the link in their inbox.
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

function ConfirmationContent() {
  const searchParams = useSearchParams();
  const email = searchParams.get("email");

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] flex flex-col items-center justify-center px-6 py-16">
      <Link
        href="/"
        className="font-heading text-2xl text-[var(--rt-ink)] mb-8 hover:text-[var(--rt-orange)] transition-colors"
      >
        rotrack
      </Link>
      <main className="w-full max-w-md">
      <Card className="border-[var(--rt-line)] bg-[var(--rt-paper)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)] text-center">
        <CardHeader>
          <CardTitle headingLevel="h1" className="font-heading text-2xl">Check your email</CardTitle>
          <CardDescription className="text-[var(--rt-ink-muted)]">
            We sent a confirmation link
            {email ? (
              <>
                {" "}
                to <span className="text-[var(--rt-ink)] font-medium">{email}</span>
              </>
            ) : (
              " to your inbox"
            )}
            . Click it to activate your account, then sign in to start tracking.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-[var(--rt-ink-soft)]">
            Didn&apos;t get it? Check spam or wait a minute and try signing up again.
          </p>
          <Button asChild className="w-full rounded-full">
            <Link href="/signin">Go to sign in</Link>
          </Button>
        </CardContent>
      </Card>
      </main>
    </div>
  );
}

export default function SignUpConfirmationPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-[var(--rt-cream)] flex items-center justify-center text-[var(--rt-ink-muted)]">
          Loading…
        </div>
      }
    >
      <ConfirmationContent />
    </Suspense>
  );
}
