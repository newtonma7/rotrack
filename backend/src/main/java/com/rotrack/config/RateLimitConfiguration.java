package com.rotrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.security.MutationRateLimitFilter;
import com.rotrack.security.MutationRateLimiter;
import com.rotrack.security.UserSearchRateLimitFilter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    private static final int USER_SEARCH_REQUESTS_PER_MINUTE = 30;

    @Bean
    MutationRateLimiter mutationRateLimiter(RateLimitProperties properties) {
        return new MutationRateLimiter(
                properties.getRequestsPerWindow(),
                properties.getWindow(),
                properties.getMaxKeys(),
                Clock.systemUTC()
        );
    }

    @Bean
    MutationRateLimitFilter mutationRateLimitFilter(
            MutationRateLimiter limiter,
            ObjectMapper objectMapper
    ) {
        return new MutationRateLimitFilter(limiter, objectMapper);
    }

    @Bean
    UserSearchRateLimitFilter userSearchRateLimitFilter(
            RateLimitProperties properties, ObjectMapper mapper
    ) {
        var limiter = new MutationRateLimiter(
                USER_SEARCH_REQUESTS_PER_MINUTE, Duration.ofMinutes(1), properties.getMaxKeys(), Clock.systemUTC());
        return new UserSearchRateLimitFilter(limiter, mapper);
    }

    @Bean
    FilterRegistrationBean<UserSearchRateLimitFilter> userSearchFilterRegistration(
            UserSearchRateLimitFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** The filter is installed inside Spring Security after JWT authentication, not twice by the servlet container. */
    @Bean
    FilterRegistrationBean<MutationRateLimitFilter> mutationRateLimitFilterRegistration(
            MutationRateLimitFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
