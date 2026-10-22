import { beforeEach, describe, expect, it, vi } from "vitest";
import { searchUsers, getFriends, getFriendRequests, getBlocks, requestFriend, acceptFriendRequest, deleteFriendRequest, removeFriend, blockUser, unblockUser } from "@/lib/api";

vi.mock("@/lib/supabase/client", () => ({ supabase: { auth: { getSession: vi.fn().mockResolvedValue({ data: { session: { access_token: "token", expires_at: Math.floor(Date.now() / 1000) + 3600 } } }), refreshSession: vi.fn() } } }));
describe("friendship API", () => {
  beforeEach(() => vi.restoreAllMocks());
  it("encodes queries and opaque cursors without sending a viewer identity", async () => {
    const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(JSON.stringify({ data: { items: [], nextCursor: null } })));
    await searchUsers("ali_");
    await getFriends("opaque+/=");
    await getFriendRequests("incoming", "opaque");
    await getBlocks();
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      "http://localhost:8080/api/v1/users/search?q=ali_",
      "http://localhost:8080/api/v1/friends?cursor=opaque%2B%2F%3D",
      "http://localhost:8080/api/v1/friend-requests?direction=incoming&cursor=opaque",
      "http://localhost:8080/api/v1/blocks",
    ]);
    for (const [, options] of fetch.mock.calls) expect(options?.headers).toEqual(expect.objectContaining({ Authorization: "Bearer token" }));
  });
  it("sends explicit transitions with username-only request bodies", async () => {
    const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(JSON.stringify({ data: { id: "id", username: "alice" } })));
    await requestFriend("alice"); await acceptFriendRequest("id/1");
    expect(fetch.mock.calls[0][1]).toEqual(expect.objectContaining({ method: "POST", body: '{"username":"alice"}' }));
    expect(fetch.mock.calls[1][0]).toBe("http://localhost:8080/api/v1/friend-requests/id%2F1/accept");
    fetch.mockImplementation(async () => new Response(null, { status: 204 }));
    await deleteFriendRequest("id/1"); await removeFriend("id/1"); await blockUser("ali/ce"); await unblockUser("ali/ce");
    expect(fetch.mock.calls.slice(2).map(([url, options]) => [url, options?.method])).toEqual([
      ["http://localhost:8080/api/v1/friend-requests/id%2F1", "DELETE"],
      ["http://localhost:8080/api/v1/friends/id%2F1", "DELETE"],
      ["http://localhost:8080/api/v1/blocks/ali%2Fce", "PUT"],
      ["http://localhost:8080/api/v1/blocks/ali%2Fce", "DELETE"],
    ]);
  });
  it("preserves the search retry-after budget", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ error: { code: "RATE_LIMITED", message: "Too many username searches" } }), { status: 429, headers: { "Retry-After": "30" } }));
    await expect(searchUsers("alice")).rejects.toMatchObject({ code: "RATE_LIMITED", retryAfterMs: 30000 });
  });
});
