package com.rotrack.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudyPresenceRepository {
    private final JdbcTemplate jdbc;

    public StudyPresenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Authorization and active-Work existence share a snapshot; no activity details leave SQL. */
    public List<Boolean> project(UUID viewer, String username) {
        return jdbc.query("""
                SELECT EXISTS (SELECT 1 FROM time_entries t
                  WHERE t.user_id=u.id AND t.activity_type='WORK' AND t.end_time IS NULL) AS studying
                FROM users u JOIN user_preferences p ON p.user_id=u.id
                WHERE u.username=? AND u.id<>? AND p.share_active_study_status IS TRUE
                  AND EXISTS (SELECT 1 FROM friendships f
                    WHERE f.state='ACCEPTED'
                      AND f.user_low_id=LEAST(?,u.id) AND f.user_high_id=GREATEST(?,u.id))
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b
                    WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=?))
                """, (rs, row) -> rs.getBoolean("studying"),
                username, viewer, viewer, viewer, viewer, viewer);
    }
}
