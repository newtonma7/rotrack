package com.rotrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsCommaSeparatedAllowedOriginsFromConfiguration() {
        contextRunner
                .withPropertyValues("rotrack.cors.allowed-origins=https://app.example.test,https://preview.example.test")
                .run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                        .containsExactly("https://app.example.test", "https://preview.example.test"));
    }

    @Test
    void rejectsWildcardResourceAndInsecureRemoteOrigins() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.test")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("https://app.example.test/path")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("http://app.example.test")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("https://user@app.example.test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsHttpOnlyForLoopbackDevelopmentOrigins() {
        assertThat(new CorsProperties(List.of("http://localhost:3000")).allowedOrigins())
                .containsExactly("http://localhost:3000");
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndRequiredRequestMetadata() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
                new CorsProperties(List.of("https://app.example.test"))
        );
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/time-entries/start");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.test");
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Accept");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.checkOrigin("https://untrusted.example.test")).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class PropertiesConfiguration {
    }
}
