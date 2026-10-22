package com.rotrack.service;

import com.rotrack.dto.FriendshipDTO.Block;
import com.rotrack.dto.FriendshipDTO.BlockPage;
import com.rotrack.dto.FriendshipDTO.Relationship;
import com.rotrack.dto.FriendshipDTO.RelationshipPage;
import com.rotrack.dto.FriendshipDTO.SearchPage;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.exception.ValidationException;
import com.rotrack.model.FriendshipPair;
import com.rotrack.repository.FriendshipRepository;
import com.rotrack.repository.FriendshipRepository.Row;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {
    private final FriendshipRepository repository;
    public FriendshipService(FriendshipRepository repository) {
        this.repository = repository;
    }

    public SearchPage search(UUID viewer, String query) {
        String prefix = username(query);
        return new SearchPage(repository.search(viewer, prefix));
    }

    public RelationshipPage friends(UUID viewer, String cursor) {
        return list(viewer, "ACCEPTED", null, cursor);
    }
    public RelationshipPage requests(UUID viewer, String direction, String cursor) {
        if (!List.of("incoming", "outgoing").contains(direction == null ? "" : direction)) {
            throw validation("direction", "Choose incoming or outgoing");
        }
        return list(viewer, "PENDING", direction, cursor);
    }

    private RelationshipPage list(UUID viewer, String state, String direction, String rawCursor) {
        Cursor cursor = decode(rawCursor);
        var rows = repository.list(viewer, state, direction, cursor == null ? null : cursor.time(), cursor == null ? null : cursor.id());
        boolean more = rows.size() > 20;
        var page = more ? rows.subList(0, 20) : rows;
        var last = page.isEmpty() ? null : page.getLast();
        return new RelationshipPage(page.stream().map(this::relationship).toList(), more ? encode(last.createdAt(), last.id()) : null);
    }

    public BlockPage blocks(UUID viewer, String rawCursor) {
        Cursor cursor = decode(rawCursor);
        var rows = repository.blocks(viewer, cursor == null ? null : cursor.time(), cursor == null ? null : cursor.id());
        boolean more = rows.size() > 20;
        var page = more ? rows.subList(0, 20) : rows;
        var last = page.isEmpty() ? null : page.getLast();
        return new BlockPage(page.stream().map(row -> new Block(row.username())).toList(), more ? encode(last.createdAt(), last.peerId()) : null);
    }

    @Transactional
    public Relationship request(UUID viewer, String rawUsername) {
        var peer = repository.peer(username(rawUsername)).orElseThrow(this::missing);
        var pair = pair(viewer, peer.id());
        repository.lock(pair);
        if (repository.blocked(pair)) throw missing();
        if (repository.relationshipExists(pair)) throw conflict();
        return new Relationship(repository.insert(pair, viewer), peer.username());
    }

    @Transactional
    public Relationship accept(UUID viewer, UUID id) {
        Row row = lockedOwned(viewer, id);
        if (repository.blocked(row.pair())) throw missing();
        if (row.requester().equals(viewer)) throw conflict();
        if (row.state().equals("PENDING")) repository.accept(id);
        return relationship(row);
    }

    @Transactional
    public void deleteRequest(UUID viewer, UUID id) {
        delete(viewer, id, "PENDING");
    }

    @Transactional
    public void removeFriend(UUID viewer, UUID id) {
        delete(viewer, id, "ACCEPTED");
    }

    private void delete(UUID viewer, UUID id, String expectedState) {
        Row row = lockedOwned(viewer, id);
        if (repository.blocked(row.pair())) throw missing();
        if (!row.state().equals(expectedState)) throw conflict();
        repository.delete(id);
    }

    @Transactional
    public void block(UUID viewer, String rawUsername) {
        var peer = repository.peer(username(rawUsername)).orElseThrow(this::missing);
        var pair = pair(viewer, peer.id());
        repository.lock(pair);
        repository.block(viewer, peer.id());
        repository.deletePair(pair);
    }

    @Transactional
    public void unblock(UUID viewer, String rawUsername) {
        repository.peer(username(rawUsername)).ifPresent(peer -> {
            var pair = pair(viewer, peer.id());
            repository.lock(pair);
            repository.unblock(viewer, peer.id());
        });
    }

    private Row lockedOwned(UUID viewer, UUID id) {
        // This first read takes no row lock. Pair lock must precede every row lock/write.
        Row first = repository.owned(viewer, id).orElseThrow(this::missing);
        repository.lock(first.pair());
        return repository.owned(viewer, id).orElseThrow(this::missing);
    }

    private FriendshipPair pair(UUID viewer, UUID peer) {
        if (viewer.equals(peer)) throw validation("username", "Choose another username");
        return FriendshipPair.of(viewer, peer);
    }

    private String username(String value) {
        if (value == null) throw validation("username", "A canonical username of 3–24 characters is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,24}")) {
            throw validation("username", "Use 3–24 letters, numbers, or underscores");
        }
        return normalized;
    }

    private Relationship relationship(Row row) {
        return new Relationship(row.id(), row.username());
    }

    private ResourceNotFoundException missing() {
        return new ResourceNotFoundException("Relationship or user not found");
    }

    private ConflictException conflict() {
        return new ConflictException("FRIENDSHIP_CONFLICT", "The relationship has changed; refresh and try again");
    }

    private ValidationException validation(String field, String message) {
        return new ValidationException(message, Map.of(field, message));
    }

    private record Cursor(Instant time, UUID id) {}
    private String encode(Instant time, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
    private Cursor decode(String raw) {
        if (raw == null) return null;
        try {
            if (raw.isEmpty() || raw.length() > 180) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            Cursor cursor = new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            if (!encode(cursor.time(), cursor.id()).equals(raw) || cursor.time().getNano() % 1000 != 0
                    || cursor.time().isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || cursor.time().isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) throw new IllegalArgumentException();
            return cursor;
        } catch (RuntimeException exception) {
            throw new InvalidCursorException("The social cursor is invalid");
        }
    }
}
