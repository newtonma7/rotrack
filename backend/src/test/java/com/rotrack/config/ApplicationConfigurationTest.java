package com.rotrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class ApplicationConfigurationTest {

    @Test
    void documentedEnvironmentNamesBindToTheRuntimeDataSourceAndSecurityConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "DATABASE_URL=jdbc:postgresql://db.example.test:5432/postgres?sslmode=verify-full&sslrootcert=/run/secrets/supabase-db-ca.crt",
                        "DATABASE_USERNAME=example_app",
                        "DATABASE_PASSWORD=redacted-example",
                        "DATABASE_CONNECTION_TIMEOUT_MS=6000",
                        "DATABASE_POOL_VALIDATION_TIMEOUT_MS=2500",
                        "DATABASE_MAXIMUM_POOL_SIZE=4",
                        "DATABASE_MINIMUM_IDLE=1",
                        "SUPABASE_JWKS_URI=https://project.example.test/auth/v1/.well-known/jwks.json",
                        "SUPABASE_ISSUER_URI=https://project.example.test/auth/v1",
                        "SUPABASE_JWT_AUDIENCE=authenticated",
                        "CORS_ALLOWED_ORIGINS=https://app.example.test"
                )
                .run(context -> {
                    HikariDataSource dataSource = context.getBean(HikariDataSource.class);
                    assertThat(dataSource.getJdbcUrl())
                            .isEqualTo("jdbc:postgresql://db.example.test:5432/postgres?sslmode=verify-full&sslrootcert=/run/secrets/supabase-db-ca.crt");
                    assertThat(dataSource.getUsername()).isEqualTo("example_app");
                    assertThat(dataSource.getConnectionTimeout()).isEqualTo(6000);
                    assertThat(dataSource.getValidationTimeout()).isEqualTo(2500);
                    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(4);
                    assertThat(dataSource.getMinimumIdle()).isEqualTo(1);

                    Environment environment = context.getEnvironment();
                    assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                            .isEqualTo("https://project.example.test/auth/v1/.well-known/jwks.json");
                    assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                            .isEqualTo("https://project.example.test/auth/v1");
                    assertThat(environment.getProperty("rotrack.security.jwt.audience"))
                            .isEqualTo("authenticated");
                    assertThat(environment.getProperty("rotrack.cors.allowed-origins"))
                            .isEqualTo("https://app.example.test");
                });
    }
}
