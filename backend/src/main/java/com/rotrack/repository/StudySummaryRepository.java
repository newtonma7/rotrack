package com.rotrack.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudySummaryRepository {
    private final JdbcTemplate jdbc;
    public StudySummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record DayTotal(LocalDate day, long seconds) {}

    /** One PostgreSQL statement authorizes and projects at most 366 days from the same snapshot. */
    public List<DayTotal> project(UUID viewer, String username, Instant now) {
        return jdbc.query("""
                WITH peer AS (
                  SELECT u.id, COALESCE(p.timezone, 'UTC') AS zone
                  FROM users u LEFT JOIN user_preferences p ON p.user_id=u.id
                  WHERE u.username=? AND u.id<>?
                    AND p.share_study_summary IS TRUE
                    AND EXISTS (SELECT 1 FROM friendships f
                      WHERE f.state='ACCEPTED'
                        AND f.user_low_id=LEAST(?,u.id) AND f.user_high_id=GREATEST(?,u.id))
                    AND NOT EXISTS (SELECT 1 FROM user_blocks b
                      WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=?))
                ), bounds AS (
                  SELECT id, zone, (? AT TIME ZONE zone)::date AS today FROM peer
                ), days AS (
                  SELECT b.id, b.zone, (b.today - offsets.n)::date AS day
                  FROM bounds b CROSS JOIN generate_series(0,365) AS offsets(n)
                ), daily AS (
                  SELECT d.day,
                    COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN FLOOR(EXTRACT(EPOCH FROM
                      (LEAST(t.end_time, (d.day + 1)::timestamp AT TIME ZONE d.zone, ?)
                       - GREATEST(t.start_time, d.day::timestamp AT TIME ZONE d.zone)))
                    ) END),0)::bigint AS seconds
                  FROM days d
                  LEFT JOIN time_entries t ON t.user_id=d.id AND t.activity_type='WORK'
                    AND t.end_time IS NOT NULL
                    AND t.start_time < LEAST((d.day + 1)::timestamp AT TIME ZONE d.zone, ?)
                    AND t.end_time > d.day::timestamp AT TIME ZONE d.zone
                  GROUP BY d.day
                )
                SELECT day, seconds FROM daily ORDER BY day
                """, (rs, row) -> new DayTotal(rs.getObject("day", LocalDate.class), rs.getLong("seconds")),
                username, viewer, viewer, viewer, viewer, viewer, java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }
}
