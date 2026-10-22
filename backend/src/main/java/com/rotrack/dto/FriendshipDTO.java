package com.rotrack.dto;

import java.util.List;
import java.util.UUID;

public final class FriendshipDTO {
    private FriendshipDTO() {}
    public enum RelationshipState { NONE, OUTGOING, INCOMING, FRIEND }
    public record SearchResult(String username, RelationshipState relationship) {}
    public record SearchPage(List<SearchResult> items) {}
    public record Relationship(UUID id, String username) {}
    public record RelationshipPage(List<Relationship> items, String nextCursor) {}
    public record Block(String username) {}
    public record BlockPage(List<Block> items, String nextCursor) {}
}
