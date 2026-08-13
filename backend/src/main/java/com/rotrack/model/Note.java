package com.rotrack.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "time_entry_id")
    private UUID timeEntryId;

    @Column(name = "attachment_owner_id")
    private UUID attachmentOwnerId;

    @Column(length = 120)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "json")
    private JsonNode contentJson;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "content_schema_version", nullable = false)
    private int contentSchemaVersion;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == 0) version = 1;
        if (contentSchemaVersion == 0) contentSchemaVersion = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID timeEntryId) { this.timeEntryId = timeEntryId; }
    public UUID getAttachmentOwnerId() { return attachmentOwnerId; }
    public void setAttachmentOwnerId(UUID attachmentOwnerId) { this.attachmentOwnerId = attachmentOwnerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public JsonNode getContentJson() { return contentJson; }
    public void setContentJson(JsonNode contentJson) { this.contentJson = contentJson; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public int getContentSchemaVersion() { return contentSchemaVersion; }
    public void setContentSchemaVersion(int contentSchemaVersion) { this.contentSchemaVersion = contentSchemaVersion; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
