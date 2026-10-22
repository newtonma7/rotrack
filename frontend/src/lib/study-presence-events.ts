const CHANNEL = "rotrack-study-presence-changed";

// Content-free invalidation only. A failed notification must never turn a committed stop into an error.
export function notifyStudyPresenceChanged(): void {
  try {
    const channel = new BroadcastChannel(CHANNEL);
    channel.postMessage(null);
    channel.close();
    return;
  } catch {
    window.dispatchEvent(new Event(CHANNEL));
  }
}

export function subscribeStudyPresenceChanged(refresh: () => void): () => void {
  window.addEventListener(CHANNEL, refresh);
  let channel: BroadcastChannel | undefined;
  try {
    channel = new BroadcastChannel(CHANNEL);
    channel.addEventListener("message", refresh);
  } catch {
    // Restricted or older browsers still refresh in-tab and poll across tabs.
  }
  return () => {
    window.removeEventListener(CHANNEL, refresh);
    channel?.close();
  };
}
