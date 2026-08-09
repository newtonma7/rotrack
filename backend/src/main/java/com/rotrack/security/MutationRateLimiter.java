package com.rotrack.security;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded fixed-window limiter for authenticated mutation requests.
 *
 * This process-local limiter is deliberately fail-closed when its bounded key store is full. It
 * provides safe per-task behavior now; a multi-task deployment must still place an equivalent
 * shared limit at the trusted edge before relying on it for fleet-wide abuse prevention.
 */
public final class MutationRateLimiter {

    public static final int MAX_REQUESTS_PER_WINDOW = 10_000;
    public static final Duration MAX_WINDOW = Duration.ofHours(1);
    public static final int MAX_KEYS = 100_000;
    private static final Duration MIN_WINDOW = Duration.ofSeconds(1);

    private final int requestsPerWindow;
    private final long windowMillis;
    private final int maxKeys;
    private final Clock clock;
    private final Map<MutationRateKey, Window> windows = new HashMap<>();

    public MutationRateLimiter(int requestsPerWindow, Duration window, int maxKeys, Clock clock) {
        validateConfiguration(requestsPerWindow, window, maxKeys);
        this.requestsPerWindow = requestsPerWindow;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void validateConfiguration(int requestsPerWindow, Duration window, int maxKeys) {
        if (requestsPerWindow < 1 || requestsPerWindow > MAX_REQUESTS_PER_WINDOW) {
            throw new IllegalArgumentException("requestsPerWindow must be between 1 and " + MAX_REQUESTS_PER_WINDOW);
        }
        if (window == null || window.compareTo(MIN_WINDOW) < 0 || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("window must be between 1 second and 1 hour");
        }
        if (maxKeys < 1 || maxKeys > MAX_KEYS) {
            throw new IllegalArgumentException("maxKeys must be between 1 and " + MAX_KEYS);
        }
    }

    public synchronized Decision tryAcquire(MutationRateKey key) {
        long now = clock.millis();
        removeExpired(now);
        Window current = windows.get(key);
        if (current == null) {
            if (windows.size() >= maxKeys) {
                return limitedMillis(windowMillis);
            }
            current = new Window(now, 0);
            windows.put(key, current);
        } else if (now - current.startedAtMillis >= windowMillis) {
            current = new Window(now, 0);
            windows.put(key, current);
        }

        if (current.count >= requestsPerWindow) {
            return limitedSeconds(windowRemaining(current.startedAtMillis, now));
        }
        current.count++;
        return new Decision(true, 0);
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<MutationRateKey, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next().getValue();
            if (now - window.startedAtMillis >= windowMillis) {
                iterator.remove();
            }
        }
    }

    private long windowRemaining(long startedAtMillis, long now) {
        return Math.max(1, (long) Math.ceil((windowMillis - Math.max(0, now - startedAtMillis)) / 1000.0));
    }

    private Decision limitedMillis(long retryAfterMillis) {
        return new Decision(false, Math.max(1, (long) Math.ceil(retryAfterMillis / 1000.0)));
    }

    private Decision limitedSeconds(long retryAfterSeconds) {
        return new Decision(false, Math.max(1, retryAfterSeconds));
    }

    private static final class Window {
        private final long startedAtMillis;
        private int count;

        private Window(long startedAtMillis, int count) {
            this.startedAtMillis = startedAtMillis;
            this.count = count;
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
