package com.rotrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_creation_replays")
@IdClass(NoteCreationReplayId.class)
public class NoteCreationReplay {
    @Id
    @Column(name = "owner_id")
    private UUID ownerId;
    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;
    @Column(nullable = false)
    private byte[] fingerprint;
    @Column(name = "note_id", nullable = false)
    private UUID noteId;
    @Column(name = "deleted_version")
    private Long deletedVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public byte[] getFingerprint() { return fingerprint; }
    public void setFingerprint(byte[] fingerprint) { this.fingerprint = fingerprint; }
    public UUID getNoteId() { return noteId; }
    public void setNoteId(UUID noteId) { this.noteId = noteId; }
    public Long getDeletedVersion() { return deletedVersion; }
    public void setDeletedVersion(Long deletedVersion) { this.deletedVersion = deletedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
