"use client";

import Link from "next/link";
import { useState } from "react";
import { supabase } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function SignUp() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    if (password !== confirmPassword) {
      setMessage("Passwords do not match");
      setLoading(false);
      return;
    }
    const { data, error } = await supabase.auth.signUp({ email, password });
    setLoading(false);
    if (error) {
      setMessage(error.message);
    } else {
      setMessage(`Check ${data?.user?.email} for a confirmation link.`);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] flex flex-col items-center justify-center px-6 py-16">
      <Link href="/" className="font-display text-2xl text-[var(--rt-ink)] mb-8 hover:text-[var(--rt-orange)] transition-colors">
        rotrack
      </Link>
      <Card className="w-full max-w-md border-[var(--rt-line)] bg-[var(--rt-paper)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)]">
        <CardHeader>
          <CardTitle className="font-display text-2xl">Sign up</CardTitle>
          <CardDescription className="text-[var(--rt-ink-muted)]">
            Start your first honest week of time-logging.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSignUp} className="space-y-4">
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
                placeholder="Create a password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="Confirm your password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={loading} className="w-full rounded-full">
              {loading ? "Creating account..." : "Sign up"}
            </Button>
          </form>
          <p className="mt-6 text-center text-sm text-[var(--rt-ink-muted)]">
            Already have an account?{" "}
            <Link href="/signin" className="text-[var(--rt-orange)] hover:underline">
              Sign in
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
