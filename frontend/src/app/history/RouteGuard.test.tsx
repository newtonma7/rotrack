/* @vitest-environment jsdom */

import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import HistoryRouteGuard from "@/app/history/RouteGuard";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/history",
}));
vi.mock("@/context/AuthProvider", () => ({
  useAuth: () => ({ user: null, loading: false }),
}));

describe("HistoryRouteGuard", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("preserves history as the sign-in return path", async () => {
    render(<HistoryRouteGuard><p>private history</p></HistoryRouteGuard>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/signin?returnTo=%2Fhistory"));
  });
});
