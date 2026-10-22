package com.rotrack.config;

import tools.jackson.databind.ObjectMapper;
import com.rotrack.observability.StructuredRequestLoggingFilter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoggingProperties.class)
public class ObservabilityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "rotrack.logging", name = "enabled", havingValue = "true")
    FilterRegistrationBean<StructuredRequestLoggingFilter> structuredRequestLoggingFilter(
            ObjectMapper objectMapper,
            LoggingProperties properties
    ) {
        properties.validateWhenEnabled();
        var registration = new FilterRegistrationBean<>(StructuredRequestLoggingFilter.production(
                objectMapper,
                Clock.systemUTC(),
                properties.getEnvironment(),
                properties.getServiceVersion()
        ));
        // Run outside and before Spring Security so rejected requests still receive one event/ID.
        // Keep ahead of Spring Security's -100 registration; Boot 4 removed its public order constant.
        registration.setOrder(-101);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
