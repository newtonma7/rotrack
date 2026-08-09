package com.rotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rotrack.logging")
public class LoggingProperties {

    private boolean enabled;
    private String environment = "staging";
    private String serviceVersion = "local";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public void validateWhenEnabled() {
        if (!enabled) {
            return;
        }
        if (!"staging".equals(environment) && !"production".equals(environment)) {
            throw new IllegalArgumentException("rotrack.logging.environment must be staging or production");
        }
        if (serviceVersion == null
                || !serviceVersion.matches("[A-Za-z0-9._-]{1,128}")
                || isPlaceholder(serviceVersion)) {
            throw new IllegalArgumentException(
                    "rotrack.logging.service-version must be an explicit immutable release identifier"
            );
        }
    }

    private boolean isPlaceholder(String value) {
        return value.equalsIgnoreCase("local")
                || value.equalsIgnoreCase("latest")
                || value.equalsIgnoreCase("unknown")
                || value.equalsIgnoreCase("replace-me")
                || value.equalsIgnoreCase("changeme")
                || value.startsWith("${")
                || value.contains("YOUR_");
    }
}
