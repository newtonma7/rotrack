"use client";
import { useState } from "react";
import { supabase } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";

export default function SignIn() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSignIn = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password, 
    });
    setLoading(false);
    if (error) {
      setMessage(error.message);
    } else {
      router.push("/home");
    }
  };

  const handleSignOut = async () => {
    await supabase.auth.signOut();
    setMessage("");
  };

  return (
    <div className="max-w-md mx-auto mt-16 p-6 bg-white rounded shadow">
      <h2 className="text-2xl mb-4 text-black">Sign in</h2>
      <form>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full mb-2 p-2 border rounded text-black"
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full mb-2 p-2 border rounded text-black"
        />

        <button
          type="button"
          onClick={handleSignIn}
          disabled={loading}
          className="flex-1 p-2 bg-blue-500 text-white rounded text-black"
        >
          Sign In
        </button>
      </form>

      <button
        onClick={handleSignOut}
        className="mt-4 text-sm text-gray-600 underline text-black"
      >
        Sign out
      </button>

      {message && <p className="mt-4 text-center text-black">{message}</p>}
    </div>
  );
}
