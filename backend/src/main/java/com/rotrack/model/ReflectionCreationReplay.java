package com.rotrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reflection_creation_replays")
@IdClass(ReflectionCreationReplayId.class)
public class ReflectionCreationReplay {
    @Id
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false)
    private byte[] fingerprint;

    @Column(name = "reflection_id", nullable = false)
    private UUID reflectionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public byte[] getFingerprint() { return fingerprint; }
    public void setFingerprint(byte[] fingerprint) { this.fingerprint = fingerprint; }
    public UUID getReflectionId() { return reflectionId; }
    public void setReflectionId(UUID reflectionId) { this.reflectionId = reflectionId; }
}
