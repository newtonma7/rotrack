/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SignUp from "@/components/auth/SignUp";

const { push, signUp } = vi.hoisted(() => ({
  push: vi.fn(),
  signUp: vi.fn(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/lib/supabase/client", () => ({
  supabase: { auth: { signUp } },
}));

function fillForm({
  username = "reader_1",
  email = "reader@example.com",
  password = "correct horse battery staple",
  confirmPassword = password,
}: Partial<Record<"username" | "email" | "password" | "confirmPassword", string>> = {}) {
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: username } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: password } });
  fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: confirmPassword } });
}

describe("SignUp", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    signUp.mockResolvedValue({ data: { user: { email: "reader@example.com" } }, error: null });
  });

  it("exposes username guidance and a field error accessibly", () => {
    render(<SignUp />);

    const username = screen.getByLabelText("Username");
    expect(username.getAttribute("aria-describedby")).toBe("username-hint");
    expect(screen.getByText("3–24 characters: lowercase letters, numbers, and underscores.")).toBeTruthy();

    fillForm({ username: "no!" });
    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("alert").textContent).toContain("lowercase letters");
    expect(username.getAttribute("aria-describedby")).toContain("username-error");
    expect(username.getAttribute("aria-invalid")).toBe("true");
  });

  it("requires a username before signup", () => {
    render(<SignUp />);
    fillForm({ username: "" });

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("alert").textContent).toBe("Username is required");
    expect(signUp).not.toHaveBeenCalled();
  });

  it.each([
    ["ab", "3–24 characters"],
    ["admin", "reserved"],
  ])("rejects %s locally", (username, errorText) => {
    render(<SignUp />);
    fillForm({ username });

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("alert").textContent).toContain(errorText);
    expect(signUp).not.toHaveBeenCalled();
  });

  it("trims and lowercases the username before signup and keeps the confirmation redirect", async () => {
    render(<SignUp />);
    fillForm({ username: "  Reader_1  " });

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    await waitFor(() => expect(signUp).toHaveBeenCalledWith({
      email: "reader@example.com",
      password: "correct horse battery staple",
      options: { data: { username: "reader_1" } },
    }));
    expect(push).toHaveBeenCalledWith("/signup/confirmation?email=reader%40example.com");
  });

  it("maps signup failures to safe generic username copy", async () => {
    signUp.mockResolvedValue({ data: null, error: { message: "duplicate key violates unique constraint" } });
    render(<SignUp />);
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toBe("That username is unavailable. Try another one.");
    expect(alert.textContent).not.toContain("duplicate");
    expect(push).not.toHaveBeenCalled();
  });

  it("maps rejected signup requests to the same safe generic copy", async () => {
    signUp.mockRejectedValue(new Error("trigger details"));
    render(<SignUp />);
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect((await screen.findByRole("alert")).textContent).toBe("That username is unavailable. Try another one.");
  });

  it("preserves password mismatch behavior", async () => {
    render(<SignUp />);
    fillForm({ confirmPassword: "different password" });

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("alert").textContent).toBe("Passwords do not match");
    expect(signUp).not.toHaveBeenCalled();
  });
});
