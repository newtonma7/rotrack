package com.rotrack.security;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Separate save budget so rich-text autosaves cannot consume timer/history capacity. */
public final class NoteRateLimiter {
    private final MutationRateLimiter delegate;

    public NoteRateLimiter(int requestsPerWindow, Duration window, int maxKeys, Clock clock) {
        this.delegate = new MutationRateLimiter(requestsPerWindow, window, maxKeys, clock);
    }

    public MutationRateLimiter.Decision tryAcquire(UUID userId) {
        return delegate.tryAcquire(new MutationRateKey(userId));
    }
}
