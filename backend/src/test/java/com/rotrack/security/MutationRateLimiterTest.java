package com.rotrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MutationRateLimiterTest {

    @Test
    void permitsConfiguredRequestsThenRecoversAtTheNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T00:00:00Z"));
        MutationRateLimiter limiter = new MutationRateLimiter(2, Duration.ofSeconds(10), 10, clock);
        MutationRateKey key = new MutationRateKey(
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );

        assertThat(limiter.tryAcquire(key).allowed()).isTrue();
        assertThat(limiter.tryAcquire(key).allowed()).isTrue();
        MutationRateLimiter.Decision limited = limiter.tryAcquire(key);
        assertThat(limited.allowed()).isFalse();
        assertThat(limited.retryAfterSeconds()).isEqualTo(10);

        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire(key).allowed()).isTrue();
    }

    @Test
    void expiresOldKeysAndRejectsNewKeysOnlyWhenTheBoundIsExhausted() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T00:00:00Z"));
        MutationRateLimiter limiter = new MutationRateLimiter(1, Duration.ofSeconds(5), 1, clock);
        MutationRateKey first = key("11111111-1111-1111-1111-111111111111");
        MutationRateKey second = key("22222222-2222-2222-2222-222222222222");

        assertThat(limiter.tryAcquire(first).allowed()).isTrue();
        assertThat(limiter.tryAcquire(second).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(5));

        assertThat(limiter.tryAcquire(second).allowed()).isTrue();
    }

    private static MutationRateKey key(String subject) {
        return new MutationRateKey(UUID.fromString(subject));
    }

    @Test
    void concurrentCallsNeverExceedConfiguredCapacity() throws Exception {
        int capacity = 17;
        int callers = 128;
        MutationRateLimiter limiter = new MutationRateLimiter(
                capacity,
                Duration.ofMinutes(1),
                10,
                Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), java.time.ZoneOffset.UTC)
        );
        MutationRateKey key = key("33333333-3333-3333-3333-333333333333");
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        try {
            Future<?>[] futures = new Future<?>[callers];
            for (int i = 0; i < callers; i++) {
                futures[i] = executor.submit(() -> {
                    start.await();
                    if (limiter.tryAcquire(key).allowed()) {
                        allowed.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(allowed).hasValue(capacity);
    }

    @Test
    void rejectsUnboundedConfigurationValues() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T00:00:00Z"));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new MutationRateLimiter(
                        MutationRateLimiter.MAX_REQUESTS_PER_WINDOW + 1,
                        Duration.ofMinutes(1),
                        10,
                        clock
                ));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new MutationRateLimiter(1, Duration.ofHours(1).plusSeconds(1), 10, clock));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new MutationRateLimiter(1, Duration.ofMinutes(1), MutationRateLimiter.MAX_KEYS + 1, clock));
    }

    private static final class MutableClock extends java.time.Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
