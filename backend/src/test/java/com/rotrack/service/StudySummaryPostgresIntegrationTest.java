package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.exception.ResourceNotFoundException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "SUPABASE_JWKS_URI=https://example.test/jwks", "SUPABASE_ISSUER_URI=https://example.test/issuer",
        "SUPABASE_JWT_AUDIENCE=authenticated", "rotrack.notes.hmac-secret=test-only-notes-hmac-secret-32-bytes"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudySummaryPostgresIntegrationTest {
    private static final UUID VIEWER = UUID.fromString("10000000-0062-4000-8000-000000000011");
    private static final UUID PEER = UUID.fromString("90000000-0062-4000-8000-000000000012");
    private static final UUID OTHER = UUID.fromString("f0000000-0062-4000-8000-000000000013");
    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");

    @Autowired StudySummaryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoBean DatabaseTlsValidator databaseTlsValidator;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("ROTRACK_TEST_DATABASE_URL");
        if (!"true".equals(System.getenv("ROTRACK_TEST_DATABASE_ISOLATED")) || url == null
                || !url.startsWith("jdbc:postgresql://") || url.contains("password=")
                || !List.of("localhost", "127.0.0.1", "::1", "[::1]").contains(URI.create(url.substring(5)).getHost())) {
            throw new IllegalStateException("An isolated loopback PostgreSQL target is required");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_USERNAME", "postgres"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("ROTRACK_TEST_DATABASE_PASSWORD", ""));
    }

    @BeforeAll void createRuntimeProbeRole() {
        jdbc.execute("CREATE ROLE rotrack_m62_summary_probe NOLOGIN BYPASSRLS IN ROLE rotrack_runtime");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO rotrack_m62_summary_probe");
        jdbc.execute("GRANT SELECT(id,username) ON users TO rotrack_m62_summary_probe");
        jdbc.execute("GRANT SELECT ON time_entries TO rotrack_m62_summary_probe");
    }
    @AfterAll void removeRuntimeProbeRole() {
        jdbc.execute("DROP OWNED BY rotrack_m62_summary_probe");
        jdbc.execute("DROP ROLE rotrack_m62_summary_probe");
    }
    private <T> T runtime(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> {
            jdbc.execute("SET LOCAL ROLE rotrack_m62_summary_probe");
            return action.get();
        });
    }

    @BeforeEach void fixtures() {
        when(clock.instant()).thenReturn(NOW);
        addUser(VIEWER, "m62_viewer"); addUser(PEER, "m62_peer"); addUser(OTHER, "m62_other");
        jdbc.update("INSERT INTO friendships(user_low_id,user_high_id,requester_id,state) VALUES (?,?,?,'ACCEPTED')", VIEWER, PEER, VIEWER);
        jdbc.update("UPDATE user_preferences SET share_study_summary=true WHERE user_id=?", PEER);
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM auth.users WHERE id IN (?,?,?)", VIEWER, PEER, OTHER); }
    private void addUser(UUID id, String username) {
        jdbc.update("INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES (?,?,jsonb_build_object('username',?))",
                id, "m62-fixture-" + id + "@example.test", username);
    }
    private void entry(UUID owner, String type, String start, String end) {
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time,end_time) VALUES (?,?::activity_type,?::timestamptz,?::timestamptz)",
                owner, type, start, end);
    }

    @Test void authorizationRequiresAcceptedUnblockedFriendAndPeerOptIn() {
        assertThat(runtime(() -> service.get(VIEWER, "m62_peer")).workSeconds()).isZero(); // zero is available
        jdbc.update("UPDATE friendships SET state='PENDING' WHERE user_low_id=? AND user_high_id=?", VIEWER, PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM friendships WHERE user_low_id=? AND user_high_id=?", VIEWER, PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("INSERT INTO friendships(user_low_id,user_high_id,requester_id,state) VALUES (?,?,?,'ACCEPTED')", VIEWER, PEER, VIEWER);
        jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?)", PEER, VIEWER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=?", PEER);
        jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?)", VIEWER, PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=?", VIEWER);
        jdbc.update("UPDATE user_preferences SET share_study_summary=false WHERE user_id=?", PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_other"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> runtime(() -> service.get(PEER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM user_preferences WHERE user_id=?", PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_missing"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void clipsSevenDayWindowRoundsEachDayAndExcludesFutureActiveAndRot() {
        entry(PEER, "WORK", "2026-06-03T23:00:00Z", "2026-06-04T01:00:00Z"); // outside seven days
        entry(PEER, "WORK", "2026-06-05T00:00:00Z", "2026-06-05T00:00:01.900Z");
        entry(PEER, "WORK", "2026-06-10T23:59:59.500Z", "2026-06-11T00:00:00.600Z");
        entry(PEER, "WORK", "2026-06-11T11:59:59Z", "2026-06-11T12:00:00Z");
        entry(PEER, "WORK", "2026-06-11T13:00:00Z", "2026-06-11T14:00:00Z");
        entry(PEER, "ROT", "2026-06-11T10:00:00Z", "2026-06-11T11:00:00Z");
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time) VALUES (?, 'WORK'::activity_type, '2026-06-12T08:00:00Z')", PEER);
        var result = runtime(() -> service.get(VIEWER, "m62_peer"));
        assertThat(result.workSeconds()).isEqualTo(2); // floor each entry/day segment
        assertThat(result.studyDayStreak()).isEqualTo(1); // prior day has 0 whole seconds
        assertThat(result.streakCapped()).isFalse();
    }

    @Test void savedZoneSplitsBothDstTransitionsAtActualMidnight() {
        jdbc.update("UPDATE user_preferences SET timezone='America/New_York' WHERE user_id=?", PEER);
        when(clock.instant()).thenReturn(Instant.parse("2026-03-09T04:00:00Z"));
        entry(PEER, "WORK", "2026-03-08T05:00:00Z", "2026-03-09T04:00:00Z");
        var spring = runtime(() -> service.get(VIEWER, "m62_peer"));
        assertThat(spring.workSeconds()).isEqualTo(23 * 3600);
        assertThat(spring.studyDayStreak()).isEqualTo(1);
        jdbc.update("DELETE FROM time_entries WHERE user_id=?", PEER);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T05:00:00Z"));
        entry(PEER, "WORK", "2026-11-01T04:00:00Z", "2026-11-02T05:00:00Z");
        var fall = runtime(() -> service.get(VIEWER, "m62_peer"));
        assertThat(fall.workSeconds()).isEqualTo(25 * 3600);
        assertThat(fall.studyDayStreak()).isEqualTo(1);
    }

    @Test void fractionalOffsetAndDatabaseTimezoneCannotRedefineThePeerDay() {
        jdbc.update("UPDATE user_preferences SET timezone='Asia/Kathmandu' WHERE user_id=?", PEER);
        when(clock.instant()).thenReturn(Instant.parse("2026-06-11T19:00:00Z")); // June 12 locally
        entry(PEER, "WORK", "2026-06-11T18:14:59Z", "2026-06-11T18:15:01Z");
        var result = runtime(() -> {
            jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Honolulu'");
            return service.get(VIEWER, "m62_peer");
        });
        assertThat(result.workSeconds()).isEqualTo(2);
        assertThat(result.studyDayStreak()).isEqualTo(2);
    }

    @Test void clipsEntriesAcrossSevenDayStartAndNowWithoutReadingAnotherUser() {
        entry(PEER, "WORK", "2026-06-04T23:59:59Z", "2026-06-05T00:00:01Z");
        entry(PEER, "WORK", "2026-06-11T11:59:59Z", "2026-06-11T12:00:02Z");
        entry(OTHER, "WORK", "2026-06-11T08:00:00Z", "2026-06-11T09:00:00Z");
        var result = runtime(() -> service.get(VIEWER, "m62_peer"));
        assertThat(result.workSeconds()).isEqualTo(2);
        assertThat(result.studyDayStreak()).isEqualTo(1);
    }

    @Test void queryBoundsTheStreakAndHonorsBothPendingDirections() {
        entry(PEER, "WORK", "2025-06-01T00:00:00Z", "2026-06-11T12:00:00Z");
        var full = runtime(() -> service.get(VIEWER, "m62_peer"));
        assertThat(full.studyDayStreak()).isEqualTo(366);
        assertThat(full.streakCapped()).isTrue();
        assertThat(full.workSeconds()).isEqualTo(6 * 86400 + 12 * 3600);
        jdbc.update("UPDATE friendships SET state='PENDING', requester_id=? WHERE user_low_id=? AND user_high_id=?", PEER, VIEWER, PEER);
        assertThatThrownBy(() -> runtime(() -> service.get(VIEWER, "m62_peer"))).isInstanceOf(ResourceNotFoundException.class);
    }
}
