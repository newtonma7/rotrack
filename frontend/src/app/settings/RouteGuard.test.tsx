/* @vitest-environment jsdom */

import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import SettingsRouteGuard from "@/app/settings/RouteGuard";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/settings",
}));
vi.mock("@/context/AuthProvider", () => ({
  useAuth: () => ({ user: null, loading: false }),
}));

describe("SettingsRouteGuard", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("preserves settings as the sign-in return path", async () => {
    render(<SettingsRouteGuard><p>private settings</p></SettingsRouteGuard>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/signin?returnTo=%2Fsettings"));
  });
});
