package com.rotrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.security.MutationRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SafeguardConfigurationTest {

    @Test
    void bindsNonSecretRateLimitAndLogMetadataConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfiguration.class, ObservabilityConfiguration.class)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withPropertyValues(
                        "rotrack.logging.enabled=true",
                        "logging.structured.format.console=ecs",
                        "rotrack.security.rate-limit.requests-per-window=12",
                        "rotrack.security.rate-limit.window=45s",
                        "rotrack.security.rate-limit.max-keys=42",
                        "rotrack.logging.environment=staging",
                        "rotrack.logging.service-version=release-2026-08-08"
                )
                .run(context -> {
                    assertThat(context.getBean(RateLimitProperties.class).getRequestsPerWindow()).isEqualTo(12);
                    assertThat(context.getBean(RateLimitProperties.class).getWindow().toSeconds()).isEqualTo(45);
                    assertThat(context.getBean(RateLimitProperties.class).getMaxKeys()).isEqualTo(42);
                    assertThat(context.getBean(LoggingProperties.class).getEnvironment()).isEqualTo("staging");
                    assertThat(context.getBean(LoggingProperties.class).getServiceVersion())
                            .isEqualTo("release-2026-08-08");
                    assertThat(context.getBean(MutationRateLimiter.class)).isNotNull();
                    var registrations = context.getBeansOfType(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
                    assertThat(registrations).hasSize(2);
                    assertThat(registrations.values()).anySatisfy(registration ->
                            assertThat(registration.getOrder())
                                    .isLessThan(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER)
                    );
                });
    }

    @Test
    void disablesStructuredLoggingByDefaultForLocalDevelopment() {
        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilityConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context.getBeansOfType(
                        org.springframework.boot.web.servlet.FilterRegistrationBean.class
                )).isEmpty());
    }

    @Test
    void rejectsEnabledLoggingWithTheLocalDefaultReleaseVersion() {
        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilityConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "rotrack.logging.enabled=true",
                        "rotrack.logging.environment=staging"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void rejectsUnsafeLogEnvironmentOrReleaseMetadata() {
        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilityConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "rotrack.logging.enabled=true",
                        "rotrack.logging.environment=development",
                        "rotrack.logging.service-version=release-2026-08-08"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));

        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilityConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "rotrack.logging.enabled=true",
                        "rotrack.logging.environment=staging",
                        "rotrack.logging.service-version=release with spaces"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void rejectsUnboundedRateLimitConfigurationAtStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("rotrack.security.rate-limit.requests-per-window=10001")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));

        new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("rotrack.security.rate-limit.window=2h")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));

        new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("rotrack.security.rate-limit.max-keys=100001")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void configuresStructuredRootAndDedicatedBareJsonAppenders() throws Exception {
        try (var stream = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(stream).isNotNull();
            String config = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(config).contains(
                    "REQUEST_JSON",
                    "StructuredRequestLoggingFilter",
                    "additivity=\"false\"",
                    "<pattern>%msg%n</pattern>",
                    "org.springframework.boot.logging.logback.StructuredLogEncoder",
                    "logging.structured.format.console",
                    "defaultValue=\"ecs\"",
                    "<format>${consoleStructuredFormat}</format>"
            );
        }
    }
}
