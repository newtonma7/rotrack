package com.rotrack.repository;

import com.rotrack.dto.FriendshipDTO.RelationshipState;
import com.rotrack.dto.FriendshipDTO.SearchResult;
import com.rotrack.model.FriendshipPair;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FriendshipRepository {
    private final JdbcTemplate jdbc;

    public FriendshipRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Peer(UUID id, String username) {}
    public record Row(UUID id, FriendshipPair pair, UUID requester, String state, Instant createdAt, String username) {}
    public record BlockRow(UUID peerId, Instant createdAt, String username) {}

    public Optional<Peer> peer(String username) {
        return jdbc.query("SELECT id, username FROM users WHERE username = ?",
                (rs, n) -> new Peer(rs.getObject("id", UUID.class), rs.getString("username")), username).stream().findFirst();
    }

    public void lock(FriendshipPair pair) {
        // Transaction-scoped, identical for reversed actors. Hash collisions only add serialization.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 61))",
                rs -> {}, pair.low() + ":" + pair.high());
    }

    public boolean blocked(FriendshipPair pair) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_blocks
                  WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))
                """, Boolean.class, pair.low(), pair.high(), pair.high(), pair.low()));
    }

    public boolean relationshipExists(FriendshipPair pair) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM friendships WHERE user_low_id=? AND user_high_id=?)",
                Boolean.class, pair.low(), pair.high()));
    }

    public UUID insert(FriendshipPair pair, UUID requester) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO friendships(id,user_low_id,user_high_id,requester_id,state) VALUES (?,?,?,?,'PENDING')",
                id, pair.low(), pair.high(), requester);
        return id;
    }

    public Optional<Row> owned(UUID viewer, UUID id) {
        return jdbc.query("""
                SELECT f.*, u.username FROM friendships f
                JOIN users u ON u.id = CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END
                WHERE f.id=? AND ? IN (f.user_low_id,f.user_high_id)
                """, (rs, n) -> row(rs), viewer, id, viewer).stream().findFirst();
    }

    public void accept(UUID id) { jdbc.update("UPDATE friendships SET state='ACCEPTED' WHERE id=?", id); }
    public void delete(UUID id) { jdbc.update("DELETE FROM friendships WHERE id=?", id); }
    public void deletePair(FriendshipPair pair) {
        jdbc.update("DELETE FROM friendships WHERE user_low_id=? AND user_high_id=?", pair.low(), pair.high());
    }
    public void block(UUID viewer, UUID peer) {
        jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?) ON CONFLICT DO NOTHING", viewer, peer);
    }
    public void unblock(UUID viewer, UUID peer) {
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?", viewer, peer);
    }

    public List<SearchResult> search(UUID viewer, String prefix) {
        return jdbc.query("""
                SELECT u.username, CASE WHEN f.state='ACCEPTED' THEN 'FRIEND'
                  WHEN f.requester_id=? THEN 'OUTGOING' WHEN f.id IS NOT NULL THEN 'INCOMING' ELSE 'NONE' END AS relationship
                FROM users u LEFT JOIN friendships f
                  ON (f.user_low_id=? AND f.user_high_id=u.id) OR (f.user_high_id=? AND f.user_low_id=u.id)
                WHERE u.id<>? AND u.username LIKE ? ESCAPE '!'
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b
                    WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
                ORDER BY u.username LIMIT 10
                """, (rs, n) -> new SearchResult(rs.getString("username"), RelationshipState.valueOf(rs.getString("relationship"))),
                viewer, viewer, viewer, viewer, prefix.replace("_", "!_") + "%", viewer, viewer);
    }

    public List<Row> list(UUID viewer, String state, String direction, Instant before, UUID beforeId) {
        // Only these constant clauses vary; all external values remain bound parameters.
        String directionClause = direction == null ? "" : direction.equals("outgoing") ? " AND f.requester_id=?" : " AND f.requester_id<>?";
        String cursorClause = before == null ? "" : " AND (f.created_at,f.id)<(?,?)";
        var args = new java.util.ArrayList<Object>(List.of(viewer, viewer, state));
        if (direction != null) args.add(viewer);
        if (before != null) { args.add(Timestamp.from(before)); args.add(beforeId); }
        args.add(viewer); args.add(viewer);
        return jdbc.query("""
                SELECT f.*,u.username FROM friendships f
                JOIN users u ON u.id=CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END
                WHERE ? IN (f.user_low_id,f.user_high_id) AND f.state=?
                """ + directionClause + cursorClause + """
                 AND NOT EXISTS (SELECT 1 FROM user_blocks b
                   WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))
                ORDER BY f.created_at DESC,f.id DESC LIMIT 21
                """, (rs, n) -> row(rs), args.toArray());
    }

    public List<BlockRow> blocks(UUID viewer, Instant before, UUID beforeId) {
        String cursorClause = before == null ? "" : " AND (b.created_at,b.blocked_id)<(?,?)";
        Object[] args = before == null ? new Object[]{viewer} : new Object[]{viewer, Timestamp.from(before), beforeId};
        return jdbc.query("""
                SELECT b.blocked_id,b.created_at,u.username FROM user_blocks b JOIN users u ON u.id=b.blocked_id
                WHERE b.blocker_id=?
                """ + cursorClause + " ORDER BY b.created_at DESC,b.blocked_id DESC LIMIT 21",
                (rs, n) -> new BlockRow(rs.getObject("blocked_id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getString("username")), args);
    }

    private Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class),
                new FriendshipPair(rs.getObject("user_low_id", UUID.class), rs.getObject("user_high_id", UUID.class)),
                rs.getObject("requester_id", UUID.class), rs.getString("state"), rs.getTimestamp("created_at").toInstant(), rs.getString("username"));
    }
}
