package com.rotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rotrack.notes")
public class NotesProperties {
    private boolean writesEnabled = true;
    private String hmacSecret = "";

    public boolean isWritesEnabled() { return writesEnabled; }
    public void setWritesEnabled(boolean writesEnabled) { this.writesEnabled = writesEnabled; }
    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
}
