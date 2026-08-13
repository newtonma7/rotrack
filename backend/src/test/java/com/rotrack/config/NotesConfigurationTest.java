package com.rotrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotesConfigurationTest {
    @Test
    void failsClosedWhenWritesAreEnabledWithoutTheRuntimeSecret() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotesConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("rotrack.notes.writes-enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void allowsReadOnlyStartupWithoutASecret() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotesConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("rotrack.notes.writes-enabled=false")
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }
}
