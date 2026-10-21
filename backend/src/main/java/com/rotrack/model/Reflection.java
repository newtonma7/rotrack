package com.rotrack.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reflections")
public class Reflection {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone_at_creation", nullable = false, updatable = false)
    private String timeZoneAtCreation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "json")
    private JsonNode contentJson;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "content_schema_version", nullable = false)
    private int contentSchemaVersion;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        // PostgreSQL stores microseconds. Match that precision before returning the first write
        // so a later read or idempotent replay has the same authoritative timestamp.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == 0) version = 1;
        if (contentSchemaVersion == 0) contentSchemaVersion = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDate getLocalDate() { return localDate; }
    public void setLocalDate(LocalDate localDate) { this.localDate = localDate; }
    public String getTimeZoneAtCreation() { return timeZoneAtCreation; }
    public void setTimeZoneAtCreation(String timeZoneAtCreation) { this.timeZoneAtCreation = timeZoneAtCreation; }
    public JsonNode getContentJson() { return contentJson; }
    public void setContentJson(JsonNode contentJson) { this.contentJson = contentJson; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public int getContentSchemaVersion() { return contentSchemaVersion; }
    public void setContentSchemaVersion(int contentSchemaVersion) { this.contentSchemaVersion = contentSchemaVersion; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
