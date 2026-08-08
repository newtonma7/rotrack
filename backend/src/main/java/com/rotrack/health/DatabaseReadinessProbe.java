package com.rotrack.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

/**
 * Readiness reflects the database because authenticated application requests
 * cannot be served safely without the ownership-scoped persistence boundary.
 * A short cache makes public orchestrator polling consume at most one pool checkout per TTL per task.
 */
@Component
public class DatabaseReadinessProbe {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final Duration cacheTtl;
    private final Clock clock;
    private volatile CachedResult cachedResult = new CachedResult(false, Instant.MIN);

    @Autowired
    public DatabaseReadinessProbe(
            DataSource dataSource,
            @Value("${rotrack.health.readiness-cache-ttl:5s}") String cacheTtl
    ) {
        this(dataSource, DurationStyle.detectAndParse(cacheTtl), Clock.systemUTC());
    }

    DatabaseReadinessProbe(DataSource dataSource, Duration cacheTtl, Clock clock) {
        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("Readiness cache TTL must be positive");
        }
        this.dataSource = dataSource;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    public boolean isReady() {
        Instant now = clock.instant();
        CachedResult current = cachedResult;
        if (now.isBefore(current.expiresAt())) {
            return current.ready();
        }

        synchronized (this) {
            now = clock.instant();
            current = cachedResult;
            if (now.isBefore(current.expiresAt())) {
                return current.ready();
            }
            boolean ready = probeDatabase();
            cachedResult = new CachedResult(ready, now.plus(cacheTtl));
            return ready;
        }
    }

    private boolean probeDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
    }

    private record CachedResult(boolean ready, Instant expiresAt) {
    }
}
