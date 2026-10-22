// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StudyPresenceControl } from "./StudyPresenceControl";
import { getStudyPresence } from "@/lib/api";
import { notifyStudyPresenceChanged } from "@/lib/study-presence-events";
import { ApiRequestError } from "@/lib/api-errors";

const auth = vi.hoisted(() => ({ user: { id: "account-a" } }));
vi.mock("@/context/AuthProvider", () => ({ useAuth: () => auth }));
vi.mock("@/lib/api", () => ({ getStudyPresence: vi.fn() }));
let visibility: DocumentVisibilityState = "visible";
beforeEach(() => {
  vi.useFakeTimers(); vi.resetAllMocks();
  vi.stubGlobal("BroadcastChannel", undefined);
  auth.user = { id: "account-a" }; visibility = "visible";
  vi.spyOn(document, "visibilityState", "get").mockImplementation(() => visibility);
  vi.mocked(getStudyPresence).mockResolvedValue({ studying: true });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
async function settle() { await act(async () => {}); }
function hide() { visibility = "hidden"; fireEvent(document, new Event("visibilitychange")); }
function show() { visibility = "visible"; fireEvent(document, new Event("visibilitychange")); }

it("loads immediately then every 30 seconds and never labels false as offline or Rot", async () => {
  render(<StudyPresenceControl username="alice" />);
  await settle();
  expect(screen.getByText("Studying now")).toBeTruthy();
  await act(async () => { await vi.advanceTimersByTimeAsync(29999); });
  expect(getStudyPresence).toHaveBeenCalledTimes(1);
  vi.mocked(getStudyPresence).mockResolvedValue({ studying: false });
  await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  expect(getStudyPresence).toHaveBeenCalledTimes(2);
  expect(screen.getByText("Not studying")).toBeTruthy();
  expect(screen.queryByText(/offline|rot/i)).toBeNull();
});

it("clears on hide, stops polling, and refreshes immediately on return", async () => {
  render(<StudyPresenceControl username="alice" />); await settle();
  hide();
  expect(screen.queryByText("Studying now")).toBeNull();
  await act(async () => { await vi.advanceTimersByTimeAsync(90000); });
  expect(getStudyPresence).toHaveBeenCalledTimes(1);
  show(); await settle();
  expect(getStudyPresence).toHaveBeenCalledTimes(2);
  expect(screen.getByText("Studying now")).toBeTruthy();
});

it("does not fetch while initially hidden or after unmount", async () => {
  visibility = "hidden";
  const view = render(<StudyPresenceControl username="alice" />); await settle();
  expect(getStudyPresence).not.toHaveBeenCalled();
  show(); await settle();
  const signal = vi.mocked(getStudyPresence).mock.calls[0][1];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => { await vi.advanceTimersByTimeAsync(60000); notifyStudyPresenceChanged(); });
  expect(getStudyPresence).toHaveBeenCalledTimes(1);
});

it("aborts and ignores late responses after hide or when a newer poll completes", async () => {
  let resolve: (value: { studying: boolean }) => void = () => { throw new Error("no pending request"); };
  vi.mocked(getStudyPresence).mockReturnValueOnce(new Promise((done) => { resolve = done; }));
  render(<StudyPresenceControl username="alice" />);
  const signal = vi.mocked(getStudyPresence).mock.calls[0][1];
  hide(); expect(signal?.aborted).toBe(true);
  await act(async () => { resolve({ studying: true }); });
  expect(screen.queryByText("Studying now")).toBeNull();
  vi.mocked(getStudyPresence).mockReturnValueOnce(new Promise((done) => { resolve = done; }));
  show();
  vi.mocked(getStudyPresence).mockResolvedValue({ studying: false });
  await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
  await act(async () => { resolve({ studying: true }); });
  expect(screen.getByText("Not studying")).toBeTruthy();
});

it("remounts across account and peer changes without resurrecting private values", async () => {
  const view = render(<StudyPresenceControl username="alice" />); await settle();
  vi.mocked(getStudyPresence).mockReturnValue(new Promise(() => {}));
  auth.user = { id: "account-b" }; view.rerender(<StudyPresenceControl username="alice" />);
  expect(screen.queryByText("Studying now")).toBeNull();
  auth.user = { id: "account-a" }; view.rerender(<StudyPresenceControl username="alice" />);
  expect(screen.queryByText("Studying now")).toBeNull();
  view.rerender(<StudyPresenceControl username="bob" />);
  expect(getStudyPresence).toHaveBeenLastCalledWith("bob", expect.any(AbortSignal));
});

it("clears old values on unavailable/error and allows explicit keyboard-accessible retry", async () => {
  render(<StudyPresenceControl username="alice" />); await settle();
  vi.mocked(getStudyPresence).mockRejectedValueOnce(new ApiRequestError("Not found", 404, "NOT_FOUND", {}));
  await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
  expect(screen.queryByText("Studying now")).toBeNull();
  expect(screen.getByText("Presence unavailable")).toBeTruthy();
  vi.mocked(getStudyPresence).mockRejectedValueOnce(new Error("offline"));
  fireEvent.click(screen.getByRole("button", { name: "Retry presence for alice" })); await settle();
  expect(screen.getByText("Could not load presence")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Retry presence for alice" })); await settle();
  expect(screen.getByText("Studying now")).toBeTruthy();
});

it("refreshes on local lifecycle invalidation only when visible", async () => {
  render(<StudyPresenceControl username="alice" />); await settle();
  await act(async () => { notifyStudyPresenceChanged(); });
  expect(getStudyPresence).toHaveBeenCalledTimes(2);
  hide();
  await act(async () => { notifyStudyPresenceChanged(); });
  expect(getStudyPresence).toHaveBeenCalledTimes(2);
  show(); await settle();
  expect(getStudyPresence).toHaveBeenCalledTimes(3);
});
