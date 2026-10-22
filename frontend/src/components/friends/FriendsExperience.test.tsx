// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FriendsExperience } from "@/components/friends/FriendsExperience";
import { FriendSearch } from "@/components/friends/FriendSearch";
import * as api from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import type { UserSearchPage } from "@/types/friendships";

vi.mock("@/lib/api", () => ({ getFriends: vi.fn(), getFriendRequests: vi.fn(), getBlocks: vi.fn(), searchUsers: vi.fn(), requestFriend: vi.fn(), acceptFriendRequest: vi.fn(), deleteFriendRequest: vi.fn(), removeFriend: vi.fn(), blockUser: vi.fn(), unblockUser: vi.fn() }));
const empty = { items: [], nextCursor: null };
const alice = { id: "relationship-id", username: "alice" };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getFriends).mockResolvedValue(empty);
  vi.mocked(api.getFriendRequests).mockResolvedValue(empty);
  vi.mocked(api.getBlocks).mockResolvedValue(empty);
  vi.mocked(api.searchUsers).mockResolvedValue({ items: [] });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

describe("friendship workspace", () => {
  it("shows honest empty states without searching before three characters", async () => {
    render(<FriendsExperience />);
    await screen.findByText(/No friends yet/);
    expect(screen.getByText("You haven't blocked anyone.")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "ab" } });
    expect(api.searchUsers).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Incoming" }));
    await screen.findByText(/No incoming requests/);
    expect(api.getFriendRequests).toHaveBeenCalledWith("incoming", undefined);
  });

  it("accepts incoming requests and refreshes the lists without reversing a request", async () => {
    vi.mocked(api.getFriendRequests).mockResolvedValueOnce({ items: [alice], nextCursor: null }).mockResolvedValue(empty);
    render(<FriendsExperience />);
    fireEvent.click(screen.getByRole("button", { name: "Incoming" }));
    fireEvent.click(await screen.findByRole("button", { name: "Accept alice" }));
    await screen.findByText("You're now friends with @alice.");
    await waitFor(() => expect(api.getFriendRequests).toHaveBeenCalledTimes(2));
    expect(api.acceptFriendRequest).toHaveBeenCalledWith(alice.id);
    expect(api.requestFriend).not.toHaveBeenCalled();
  });

  it("confirms removal and blocking and never mutates on cancel", async () => {
    vi.mocked(api.getFriends).mockResolvedValue({ items: [alice], nextCursor: null });
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<FriendsExperience />);
    fireEvent.click(await screen.findByRole("button", { name: "Remove alice" }));
    expect(api.removeFriend).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Block alice" }));
    expect(api.blockUser).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    fireEvent.click(screen.getByRole("button", { name: "Block alice" }));
    await screen.findByText("@alice blocked.");
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining("Unblocking will not restore"));
    expect(api.blockUser).toHaveBeenCalledWith("alice");
  });

  it("supports explicit cancellation, decline, removal and unblock", async () => {
    vi.mocked(api.getFriends).mockResolvedValue({ items: [alice], nextCursor: null });
    vi.mocked(api.getFriendRequests).mockResolvedValue({ items: [alice], nextCursor: null });
    vi.mocked(api.getBlocks).mockResolvedValue({ items: [{ username: "bob" }], nextCursor: null });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<FriendsExperience />);
    fireEvent.click(await screen.findByRole("button", { name: "Remove alice" }));
    await waitFor(() => expect(api.removeFriend).toHaveBeenCalledWith(alice.id));
    await screen.findByText("@alice removed from friends.");
    fireEvent.click(screen.getByRole("button", { name: "Sent" }));
    fireEvent.click(await screen.findByRole("button", { name: "Cancel request to alice" }));
    await screen.findByText("Request cancelled.");
    fireEvent.click(screen.getByRole("button", { name: "Incoming" }));
    fireEvent.click(await screen.findByRole("button", { name: "Decline alice" }));
    await screen.findByText("Request declined.");
    expect(api.deleteFriendRequest).toHaveBeenCalledTimes(2);
    fireEvent.click(await screen.findByRole("button", { name: "Unblock bob" }));
    await screen.findByText("@bob unblocked.");
    expect(api.unblockUser).toHaveBeenCalledWith("bob");
  });

  it("offers manual recovery after loading or ambiguous mutation failures", async () => {
    vi.mocked(api.getFriends).mockRejectedValueOnce(new Error("offline")).mockResolvedValue({ items: [alice], nextCursor: null });
    vi.mocked(api.removeFriend).mockRejectedValue(new Error("response lost"));
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<FriendsExperience />);
    fireEvent.click(await screen.findByRole("button", { name: "Retry friends" }));
    fireEvent.click(await screen.findByRole("button", { name: "Remove alice" }));
    await screen.findByRole("alert");
    expect(api.removeFriend).toHaveBeenCalledTimes(1);
    expect(api.requestFriend).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Refresh lists" })).toBeTruthy();
  });

  it("retries a failed next page at the same cursor while preserving existing entries", async () => {
    vi.mocked(api.getFriends).mockResolvedValueOnce({ items: [alice], nextCursor: "same-cursor" })
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ items: [{ id: "bob-id", username: "bob" }], nextCursor: null });
    render(<FriendsExperience />);
    fireEvent.click(await screen.findByRole("button", { name: "Load more friends" }));
    await screen.findByText("Could not load more. Try again.");
    expect(screen.getByText("@alice")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Load more friends" }));
    await screen.findByText("@bob");
    expect(api.getFriends).toHaveBeenNthCalledWith(2, "same-cursor");
    expect(api.getFriends).toHaveBeenNthCalledWith(3, "same-cursor");
  });

  it("appends opaque pages without duplicating entries", async () => {
    vi.mocked(api.getFriends).mockResolvedValueOnce({ items: [alice], nextCursor: "opaque" })
      .mockResolvedValueOnce({ items: [{ id: "bob-id", username: "bob" }], nextCursor: null });
    render(<FriendsExperience />);
    fireEvent.click(await screen.findByRole("button", { name: "Load more friends" }));
    const list = await screen.findByRole("list", { name: "friends" });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(2));
    expect(api.getFriends).toHaveBeenLastCalledWith("opaque");
  });
});

describe("username discovery", () => {
  const request = vi.fn(); const block = vi.fn();
  async function advance(ms: number) { await act(async () => { await vi.advanceTimersByTimeAsync(ms); }); }
  it("debounces, ignores stale results and requests only explicit unconnected results", async () => {
    vi.useFakeTimers();
    let resolveOld: ((page: UserSearchPage) => void) | undefined;
    vi.mocked(api.searchUsers).mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve; }))
      .mockResolvedValueOnce({ items: [{ username: "bob", relationship: "NONE" }] });
    render(<FriendSearch revision={0} busy={false} request={request} block={block} />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "ali" } });
    await advance(299); expect(api.searchUsers).not.toHaveBeenCalled();
    await advance(1);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "bob" } });
    await advance(300);
    await act(async () => resolveOld?.({ items: [{ username: "alice", relationship: "NONE" }] }));
    expect(screen.queryByText("@alice")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Add bob" }));
    expect(request).toHaveBeenCalledWith("bob");
  });

  it("retains a stale query's rate-limit deadline before the new debounce fires", async () => {
    vi.useFakeTimers();
    let rejectOld: ((error: unknown) => void) | undefined;
    vi.mocked(api.searchUsers).mockReturnValueOnce(new Promise((_, reject) => { rejectOld = reject; }))
      .mockResolvedValue({ items: [] });
    render(<FriendSearch revision={0} busy={false} request={request} block={block} />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "ali" } });
    await advance(300);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "bob" } });
    await act(async () => rejectOld?.(new ApiRequestError("Wait", 429, "RATE_LIMITED", {}, 2000)));
    await advance(300);
    expect(api.searchUsers).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/Search limit reached/)).toBeTruthy();
    await advance(3000);
    expect(api.searchUsers).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Retry search" }));
    await advance(300);
    expect(api.searchUsers).toHaveBeenLastCalledWith("bob");
  });

  it("honors Retry-After across edits and never starts a retry loop", async () => {
    vi.useFakeTimers();
    vi.mocked(api.searchUsers).mockRejectedValueOnce(new ApiRequestError("Wait", 429, "RATE_LIMITED", {}, 2000))
      .mockResolvedValue({ items: [] });
    render(<FriendSearch revision={0} busy={false} request={request} block={block} />);
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "ali" } });
    await advance(300);
    expect(screen.getByText(/Search limit reached/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "bob" } });
    await advance(300);
    expect(api.searchUsers).toHaveBeenCalledTimes(1);
    await advance(3000);
    expect(api.searchUsers).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Retry search" }));
    await advance(300);
    expect(api.searchUsers).toHaveBeenCalledTimes(2);
  });
});
