package com.rotrack.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReflectionCreationReplayId implements Serializable {
    private UUID ownerId;
    private UUID idempotencyKey;

    public ReflectionCreationReplayId() {
    }

    public ReflectionCreationReplayId(UUID ownerId, UUID idempotencyKey) {
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReflectionCreationReplayId that)) {
            return false;
        }
        return Objects.equals(ownerId, that.ownerId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, idempotencyKey);
    }
}
