"use client";

import { useAuth } from "../context/AuthProvider";
import { useState } from "react";

export default function Home() {
  const { user, loading } = useAuth();

  return (
    <div>
      {user ? <h1>Welcome {user?.email}</h1> : <h1>Please sign in</h1>}
    </div>
  );
}
