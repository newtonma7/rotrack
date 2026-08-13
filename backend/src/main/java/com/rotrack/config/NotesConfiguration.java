package com.rotrack.config;

import com.rotrack.security.NoteRateLimitFilter;
import com.rotrack.security.NoteRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotesProperties.class)
public class NotesConfiguration {

    @Bean
    com.rotrack.richtext.RichTextDocumentValidator richTextDocumentValidator(ObjectMapper objectMapper) {
        return new com.rotrack.richtext.RichTextDocumentValidator(objectMapper);
    }

    @Bean
    NoteHmac noteHmac(NotesProperties properties) {
        String secret = properties.getHmacSecret() == null ? "" : properties.getHmacSecret();
        if (properties.isWritesEnabled() && (secret.isBlank() || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalArgumentException("Notes writes require a runtime HMAC secret");
        }
        return new NoteHmac(secret, properties.isWritesEnabled());
    }

    @Bean
    NoteRateLimiter noteRateLimiter() {
        return new NoteRateLimiter(60, java.time.Duration.ofMinutes(1), 10_000, Clock.systemUTC());
    }

    @Bean
    NoteRateLimitFilter noteRateLimitFilter(NoteRateLimiter limiter, ObjectMapper objectMapper) {
        return new NoteRateLimitFilter(limiter, objectMapper);
    }

    @Bean
    FilterRegistrationBean<NoteRateLimitFilter> noteRateLimitFilterRegistration(NoteRateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
