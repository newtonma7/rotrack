// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { getStudyPresence, startSession, stopSession, getActiveSession } from "@/lib/api";
import { subscribeStudyPresenceChanged } from "@/lib/study-presence-events";

vi.mock("@/lib/supabase/client", () => ({ supabase: { auth: { getSession: vi.fn().mockResolvedValue({ data: { session: { access_token: "token", expires_at: Math.floor(Date.now() / 1000) + 3600 } } }), refreshSession: vi.fn() } } }));
beforeEach(() => { vi.stubGlobal("BroadcastChannel", undefined); });
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("encodes the peer, forwards cancellation and never caches presence", async () => {
  const controller = new AbortController();
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ data: { studying: false } })));
  await expect(getStudyPresence("ali/ce", controller.signal)).resolves.toEqual({ studying: false });
  expect(fetch).toHaveBeenCalledWith("http://localhost:8080/api/v1/social/presence/ali%2Fce", expect.objectContaining({
    signal: controller.signal, cache: "no-store", headers: expect.objectContaining({ Authorization: "Bearer token" }),
  }));
});

it("announces only successful explicit start/stop, not restoration or failure", async () => {
  const listener = vi.fn(); const unsubscribe = subscribeStudyPresenceChanged(listener);
  try {
    const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(JSON.stringify({ data: { id: "entry" } })));
    await getActiveSession(); expect(listener).not.toHaveBeenCalled();
    await startSession("WORK"); await stopSession("entry"); expect(listener).toHaveBeenCalledTimes(2);
    for (const call of listener.mock.calls) expect(call[0].type).toBe("rotrack-study-presence-changed");
    fetch.mockImplementation(async () => new Response(JSON.stringify({ error: { code: "INTERNAL_ERROR", message: "Unavailable" } }), { status: 500 }));
    await expect(startSession("WORK")).rejects.toThrow(); await expect(stopSession("entry")).rejects.toThrow();
    expect(listener).toHaveBeenCalledTimes(2);
  } finally { unsubscribe(); }
});

it("uses a content-free channel for cross-tab invalidation and closes subscriptions", async () => {
  const postMessage = vi.fn(); const close = vi.fn(); const addEventListener = vi.fn();
  vi.stubGlobal("BroadcastChannel", class {
    postMessage = postMessage; close = close; addEventListener = addEventListener;
  });
  const unsubscribe = subscribeStudyPresenceChanged(vi.fn());
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ data: { id: "entry" } })));
  await startSession("ROT", "not shared");
  expect(postMessage).toHaveBeenCalledExactlyOnceWith(null);
  expect(close).toHaveBeenCalledTimes(1);
  unsubscribe(); expect(close).toHaveBeenCalledTimes(2);
});
