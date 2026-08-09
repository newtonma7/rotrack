package com.rotrack.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rotrack.security.rate-limit")
public class RateLimitProperties {

    private int requestsPerWindow = 30;
    private Duration window = Duration.ofMinutes(1);
    private int maxKeys = 10_000;

    public int getRequestsPerWindow() {
        return requestsPerWindow;
    }

    public void setRequestsPerWindow(int requestsPerWindow) {
        this.requestsPerWindow = requestsPerWindow;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }
}
