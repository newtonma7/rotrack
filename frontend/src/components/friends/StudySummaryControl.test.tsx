// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StudySummaryControl } from "./StudySummaryControl";
import { getStudySummary } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import type { StudySummary } from "@/types/study-summary";

const auth = vi.hoisted(() => ({ user: { id: "account-a" } }));
vi.mock("@/context/AuthProvider", () => ({ useAuth: () => auth }));
vi.mock("@/lib/api", () => ({ getStudySummary: vi.fn() }));
beforeEach(() => { auth.user = { id: "account-a" }; vi.resetAllMocks(); });
afterEach(cleanup);

it("does not resurrect a revealed summary after switching accounts and back", async () => {
  vi.mocked(getStudySummary).mockResolvedValue({ workSeconds: 3600, studyDayStreak: 1, streakCapped: false });
  const view = render(<StudySummaryControl username="alice" />);
  expect(getStudySummary).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Show study summary for alice" }));
  await screen.findByText("1h 0m work · last 7 days");
  auth.user = { id: "account-b" };
  view.rerender(<StudySummaryControl username="alice" />);
  expect(screen.queryByText("1h 0m work · last 7 days")).toBeNull();
  auth.user = { id: "account-a" };
  view.rerender(<StudySummaryControl username="alice" />);
  expect(screen.queryByText("1h 0m work · last 7 days")).toBeNull();
  expect(screen.getByRole("button", { name: "Show study summary for alice" })).toBeTruthy();
});

it("ignores a late response after the peer changes or the control unmounts", async () => {
  let resolve: (summary: StudySummary) => void = () => { throw new Error("request not started"); };
  vi.mocked(getStudySummary).mockReturnValue(new Promise((done) => { resolve = done; }));
  const view = render(<StudySummaryControl username="alice" />);
  fireEvent.click(screen.getByRole("button", { name: "Show study summary for alice" }));
  expect(screen.getByRole("status").textContent).toContain("Loading");
  view.rerender(<StudySummaryControl username="bob" />);
  await act(async () => { resolve({ workSeconds: 3600, studyDayStreak: 1, streakCapped: false }); });
  expect(screen.queryByText("1h 0m work · last 7 days")).toBeNull();
  expect(screen.getByRole("button", { name: "Show study summary for bob" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Show study summary for bob" }));
  view.unmount();
  await act(async () => { resolve({ workSeconds: 7200, studyDayStreak: 2, streakCapped: false }); });
  expect(screen.queryByText("2h 0m work · last 7 days")).toBeNull();
});

it("offers explicit retry after an error and labels the study-day streak", async () => {
  vi.mocked(getStudySummary).mockRejectedValueOnce(new ApiRequestError("offline", 500, "INTERNAL_ERROR", {}))
    .mockResolvedValueOnce({ workSeconds: 0, studyDayStreak: 0, streakCapped: false });
  render(<StudySummaryControl username="alice" />);
  fireEvent.click(screen.getByRole("button", { name: "Show study summary for alice" }));
  await screen.findByRole("alert");
  expect(getStudySummary).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Retry study summary for alice" }));
  await screen.findByText("0h 0m work · last 7 days");
  expect(screen.getByText("Study-day streak: 0 days")).toBeTruthy();
});
