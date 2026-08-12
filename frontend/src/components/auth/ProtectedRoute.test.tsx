/* @vitest-environment jsdom */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";

const { replace, useAuthMock, usePathnameMock } = vi.hoisted(() => ({
  replace: vi.fn(),
  useAuthMock: vi.fn(),
  usePathnameMock: vi.fn(() => "/settings"),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }), usePathname: usePathnameMock }));
vi.mock("@/context/AuthProvider", () => ({ useAuth: useAuthMock }));

describe("ProtectedRoute", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("redirects unauthenticated visitors while preserving the requested path", async () => {
    useAuthMock.mockReturnValue({ user: null, loading: false });
    render(<ProtectedRoute><p>private content</p></ProtectedRoute>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/signin?returnTo=%2Fsettings"));
    expect(screen.queryByText("private content")).toBeNull();
  });

  it("renders protected content after auth resolves", () => {
    useAuthMock.mockReturnValue({ user: { id: "user-1" }, loading: false });
    render(<ProtectedRoute><p>private content</p></ProtectedRoute>);

    expect(screen.getByText("private content")).toBeTruthy();
  });
});
