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
    expect(screen.getByRole("link", { name: /dashboard/i }).getAttribute("href")).toBe("/dashboard");
    expect(screen.getByRole("link", { name: /tracker/i }).getAttribute("href")).toBe("/tracker");
    expect(screen.getByRole("link", { name: /settings/i }).getAttribute("href")).toBe("/settings");
  });
});
