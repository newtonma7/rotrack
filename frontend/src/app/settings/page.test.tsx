/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SettingsPage from "@/app/settings/page";
import { getPreferences, updatePreferences } from "@/lib/api";
import type { UserPreferences } from "@/types/preferences";

vi.mock("@/lib/api", () => ({ getPreferences: vi.fn(), updatePreferences: vi.fn() }));
vi.mock("@/lib/supabase/client", () => ({
  supabase: { auth: { signOut: vi.fn() } },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const preferences: UserPreferences = {
  timeZone: "America/New_York",
  dailyWorkGoalMinutes: 90,
  shareStudySummary: false,
  shareActiveStudyStatus: true,
};

describe("SettingsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading and then the saved preferences", async () => {
    vi.mocked(getPreferences).mockResolvedValue(preferences);

    render(<SettingsPage />);

    expect(screen.getByRole("status").textContent).toMatch(/loading preferences/i);
    expect(await screen.findByDisplayValue("America/New_York")).toBeTruthy();
    expect(screen.getByDisplayValue("90")).toBeTruthy();
    expect((screen.getByRole("checkbox", { name: /share active study status/i }) as HTMLInputElement).checked).toBe(true);
  });

  it("shows a retry action when loading fails", async () => {
    vi.mocked(getPreferences)
      .mockRejectedValueOnce(new Error("Preferences unavailable"))
      .mockResolvedValueOnce(preferences);

    render(<SettingsPage />);

    expect((await screen.findByRole("alert")).textContent).toContain("Preferences unavailable");
    fireEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => expect(getPreferences).toHaveBeenCalledTimes(2));
    expect(await screen.findByDisplayValue("America/New_York")).toBeTruthy();
  });

  it("rejects an invalid IANA timezone before sending it", async () => {
    vi.mocked(getPreferences).mockResolvedValue(preferences);

    render(<SettingsPage />);
    await screen.findByDisplayValue("America/New_York");
    fireEvent.change(screen.getByLabelText(/saved timezone/i), { target: { value: "not/a/timezone" } });
    fireEvent.click(screen.getByRole("button", { name: /save preferences/i }));

    expect((await screen.findByRole("alert")).textContent).toContain("valid IANA timezone");
    expect(updatePreferences).not.toHaveBeenCalled();
  });

  it("shows saving and saved states for a valid update", async () => {
    vi.mocked(getPreferences).mockResolvedValue(preferences);
    let resolveUpdate!: (value: UserPreferences) => void;
    vi.mocked(updatePreferences).mockReturnValue(new Promise((resolve) => { resolveUpdate = resolve; }));

    render(<SettingsPage />);
    await screen.findByDisplayValue("America/New_York");
    fireEvent.change(screen.getByLabelText(/daily work goal/i), { target: { value: "120" } });
    fireEvent.click(screen.getByRole("button", { name: /save preferences/i }));

    expect((screen.getByRole("button", { name: /saving preferences/i }) as HTMLButtonElement).disabled).toBe(true);
    resolveUpdate({ ...preferences, dailyWorkGoalMinutes: 120 });

    await waitFor(() => expect(screen.getByText(/preferences saved/i)).toBeTruthy());
    expect(updatePreferences).toHaveBeenCalledWith({ ...preferences, dailyWorkGoalMinutes: 120 });
  });
});
