package com.rotrack.model;

import java.util.UUID;

public record FriendshipPair(UUID low, UUID high) {
    public static FriendshipPair of(UUID first, UUID second) {
        // PostgreSQL UUID order is unsigned; UUID.compareTo uses signed longs.
        return first.toString().compareTo(second.toString()) < 0
                ? new FriendshipPair(first, second) : new FriendshipPair(second, first);
    }
}
