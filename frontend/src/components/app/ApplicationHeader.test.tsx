/* @vitest-environment jsdom */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApplicationHeader } from "@/components/app/ApplicationHeader";

const { pathname } = vi.hoisted(() => ({ pathname: vi.fn(() => "/history") }));
vi.mock("next/navigation", () => ({ usePathname: pathname, useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/supabase/client", () => ({ supabase: { auth: { signOut: vi.fn() } } }));

describe("ApplicationHeader", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("marks the current app route and keeps all app destinations available", () => {
    render(<ApplicationHeader />);

    expect(screen.getByText("history").getAttribute("aria-current")).toBe("page");
    const dashboard = screen.getByRole("link", { name: /dashboard/i });
    const tracker = screen.getByRole("link", { name: /tracker/i });
    expect(dashboard.getAttribute("href")).toBe("/dashboard");
    expect(tracker.getAttribute("href")).toBe("/tracker");
    expect(dashboard.className).not.toContain("hidden");
    expect(tracker.className).not.toContain("hidden");
    expect(screen.getByRole("link", { name: /settings/i }).getAttribute("href")).toBe("/settings");
  });
});
