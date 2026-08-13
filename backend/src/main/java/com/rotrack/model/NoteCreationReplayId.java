package com.rotrack.model;

import java.io.Serializable;
import java.util.UUID;

public class NoteCreationReplayId implements Serializable {
    private UUID ownerId;
    private UUID idempotencyKey;

    public NoteCreationReplayId() {}
    public NoteCreationReplayId(UUID ownerId, UUID idempotencyKey) {
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
    }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoteCreationReplayId other)) return false;
        return java.util.Objects.equals(ownerId, other.ownerId)
                && java.util.Objects.equals(idempotencyKey, other.idempotencyKey);
    }
    @Override public int hashCode() { return java.util.Objects.hash(ownerId, idempotencyKey); }
}
