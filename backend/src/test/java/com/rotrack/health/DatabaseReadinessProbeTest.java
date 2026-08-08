package com.rotrack.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class DatabaseReadinessProbeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Test
    void springSelectsTheProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withBean(DatabaseReadinessProbe.class)
                .withPropertyValues("rotrack.health.readiness-cache-ttl=7s")
                .run(context -> assertThat(context)
                        .hasSingleBean(DatabaseReadinessProbe.class));
    }

    @Test
    void reportsReadyOnlyWhenTheDatabaseConnectionValidates() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        assertThat(probe().isReady()).isTrue();
        verify(connection).close();
    }

    @Test
    void reportsNotReadyWhenValidationFails() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        assertThat(probe().isReady()).isFalse();
        verify(connection).close();
    }

    @Test
    void reportsNotReadyWithoutPropagatingConnectionFailures() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection details"));

        assertThat(probe().isReady()).isFalse();
    }

    @Test
    void cachesReadinessToBoundPublicPoolConsumption() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        DatabaseReadinessProbe probe = probe();

        assertThat(probe.isReady()).isTrue();
        assertThat(probe.isReady()).isTrue();
        assertThat(probe.isReady()).isTrue();

        verify(dataSource, times(1)).getConnection();
    }

    private DatabaseReadinessProbe probe() {
        return new DatabaseReadinessProbe(dataSource, Duration.ofSeconds(5), CLOCK);
    }
}
