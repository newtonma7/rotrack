"use client";

import Link from "next/link";
import { useState } from "react";
import { supabase } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function SignIn() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { error } = await supabase.auth.signInWithPassword({
      email,
      password,
    });
    setLoading(false);
    if (error) {
      setMessage(error.message);
    } else {
      // Authenticated users land on dashboard (stats home) rather than public marketing page.
      router.push("/dashboard");
    }
  };

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] flex flex-col items-center justify-center px-6 py-16">
      <Link href="/" className="font-heading text-2xl text-[var(--rt-ink)] mb-8 hover:text-[var(--rt-orange)] transition-colors">
        rotrack
      </Link>
      <Card className="w-full max-w-md border-[var(--rt-line)] bg-[var(--rt-paper)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)]">
        <CardHeader>
          <CardTitle className="font-heading text-2xl">Sign in</CardTitle>
          <CardDescription className="text-[var(--rt-ink-muted)]">
            Welcome back. Pick up where you left off.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSignIn} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                placeholder="Your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={loading} className="w-full rounded-full">
              {loading ? "Signing in..." : "Sign in"}
            </Button>
          </form>
          <p className="mt-6 text-center text-sm text-[var(--rt-ink-muted)]">
            No account?{" "}
            <Link href="/signup" className="text-[var(--rt-orange)] hover:underline">
              Sign up
            </Link>
          </p>
          {message && (
            <p className="mt-4 text-center text-sm text-[var(--rt-ink-soft)]">{message}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
