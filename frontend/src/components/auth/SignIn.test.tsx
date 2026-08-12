/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SignIn from "@/components/auth/SignIn";

const { push, signInWithPassword } = vi.hoisted(() => ({
  push: vi.fn(),
  signInWithPassword: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("returnTo=%2Fsettings"),
}));
vi.mock("@/lib/supabase/client", () => ({
  supabase: { auth: { signInWithPassword } },
}));

describe("SignIn", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    signInWithPassword.mockResolvedValue({ error: null });
  });

  it("returns to the protected route that sent the user to sign in", async () => {
    render(<SignIn />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "reader@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/settings"));
  });
});
