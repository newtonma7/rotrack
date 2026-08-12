"use client";

/**
 * Sign-up form — creates a Supabase Auth user, then routes to the confirmation splash.
 *
 * Layer: frontend auth UI. Data flow: form → supabase.auth.signUp() → Supabase sends
 * confirmation email → user lands on /signup/confirmation (session may be null until verified).
 */

import Link from "next/link";
import { useState } from "react";
import { supabase } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const RESERVED_USERNAMES = new Set([
  "admin",
  "api",
  "support",
  "help",
  "rotrack",
  "signin",
  "signup",
  "confirmation",
  "dashboard",
  "tracker",
  "settings",
]);

const SIGNUP_ERROR_MESSAGE = "That username is unavailable. Try another one.";
const GENERAL_SIGNUP_ERROR_MESSAGE = "Unable to create account. Please check your details and try again.";

function validateUsername(username: string): string | null {
  if (!username) return "Username is required";
  if (username.length < 3 || username.length > 24) return "Username must be 3–24 characters";
  if (!/^[a-z0-9_]+$/.test(username)) return "Use only lowercase letters, numbers, or underscores";
  if (RESERVED_USERNAMES.has(username)) return "That username is reserved. Try another one.";
  return null;
}

export default function SignUp() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage(null);

    if (password !== confirmPassword) {
      setMessage("Passwords do not match");
      setLoading(false);
      return;
    }

    const normalizedUsername = username.trim().toLowerCase();
    const validationError = validateUsername(normalizedUsername);
    setUsernameError(validationError);
    if (validationError) {
      setLoading(false);
      return;
    }

    setUsername(normalizedUsername);
    try {
      const { error } = await supabase.auth.signUp({
        email,
        password,
        options: { data: { username: normalizedUsername } },
      });
      if (error) {
        setMessage(error.code === "unexpected_failure" ? SIGNUP_ERROR_MESSAGE : GENERAL_SIGNUP_ERROR_MESSAGE);
      } else {
        router.push("/signup/confirmation");
      }
    } catch {
      setMessage(GENERAL_SIGNUP_ERROR_MESSAGE);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--rt-cream)] flex flex-col items-center justify-center px-6 py-16">
      <Link href="/" className="font-heading text-2xl text-[var(--rt-ink)] mb-8 hover:text-[var(--rt-orange)] transition-colors">
        rotrack
      </Link>
      <main className="w-full max-w-md">
      <Card className="border-[var(--rt-line)] bg-[var(--rt-paper)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)]">
        <CardHeader>
          <CardTitle headingLevel="h1" className="font-heading text-2xl">Sign up</CardTitle>
          <CardDescription className="text-[var(--rt-ink-muted)]">
            Start your first honest week of time-logging.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSignUp} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                type="text"
                placeholder="your_username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  if (usernameError) setUsernameError(null);
                }}
                onInvalid={() => setUsernameError("Username is required")}
                required
                aria-required="true"
                aria-invalid={usernameError ? "true" : undefined}
                aria-describedby={usernameError ? "username-hint username-error" : "username-hint"}
              />
              <p id="username-hint" className="text-xs text-[var(--rt-ink-muted)]">
                3–24 characters: lowercase letters, numbers, and underscores.
              </p>
              {usernameError && (
                <p id="username-error" role="alert" className="text-sm text-[var(--rt-ink-soft)]">
                  {usernameError}
                </p>
              )}
            </div>
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
            {loading && <p role="status" aria-live="polite" className="sr-only">Creating account...</p>}
          </form>
          <p className="mt-6 text-center text-sm text-[var(--rt-ink-muted)]">
            Already have an account?{" "}
            <Link href="/signin" className="text-[var(--rt-orange)] hover:underline">
              Sign in
            </Link>
          </p>
          {message && (
            <p role="alert" className="mt-4 text-center text-sm text-[var(--rt-ink-soft)]">{message}</p>
          )}
        </CardContent>
      </Card>
      </main>
    </div>
  );
}
