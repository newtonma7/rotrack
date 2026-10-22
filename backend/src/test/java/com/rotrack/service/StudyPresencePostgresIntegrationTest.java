package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.exception.ValidationException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "SUPABASE_JWKS_URI=https://example.test/jwks", "SUPABASE_ISSUER_URI=https://example.test/issuer",
        "SUPABASE_JWT_AUDIENCE=authenticated", "rotrack.notes.hmac-secret=test-only-notes-hmac-secret-32-bytes"
})
@EnabledIfSystemProperty(named = "rotrack.postgres.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudyPresencePostgresIntegrationTest {
    private static final UUID VIEWER = UUID.fromString("10000000-0063-4000-8000-000000000011");
    private static final UUID PEER = UUID.fromString("90000000-0063-4000-8000-000000000012");
    private static final UUID OTHER = UUID.fromString("f0000000-0063-4000-8000-000000000013");
    @Autowired StudyPresenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean DatabaseTlsValidator databaseTlsValidator;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
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

    @BeforeAll void role() {
        jdbc.execute("CREATE ROLE rotrack_m63_presence_probe NOLOGIN BYPASSRLS IN ROLE rotrack_runtime");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO rotrack_m63_presence_probe");
        jdbc.execute("GRANT SELECT(id,username) ON users TO rotrack_m63_presence_probe");
        jdbc.execute("GRANT SELECT ON time_entries TO rotrack_m63_presence_probe");
    }
    @AfterAll void removeRole() {
        jdbc.execute("DROP OWNED BY rotrack_m63_presence_probe");
        jdbc.execute("DROP ROLE rotrack_m63_presence_probe");
    }
    private <T> T runtime(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> {
            jdbc.execute("SET LOCAL ROLE rotrack_m63_presence_probe");
            return action.get();
        });
    }
    @BeforeEach void fixtures() {
        addUser(VIEWER, "m63_viewer"); addUser(PEER, "m63_peer"); addUser(OTHER, "m63_other");
        jdbc.update("INSERT INTO friendships(user_low_id,user_high_id,requester_id,state) VALUES (?,?,?,'ACCEPTED')", VIEWER, PEER, VIEWER);
        jdbc.update("UPDATE user_preferences SET share_active_study_status=true WHERE user_id=?", PEER);
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM auth.users WHERE id IN (?,?,?)", VIEWER, PEER, OTHER); }
    private void addUser(UUID id, String username) {
        jdbc.update("INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES (?,?,jsonb_build_object('username',?))",
                id, "m63-fixture-" + id + "@example.test", username);
    }
    private void unavailable(UUID viewer, String username) {
        assertThatThrownBy(() -> runtime(() -> service.get(viewer, username)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Study presence not found");
    }
    private boolean studying() { return runtime(() -> service.get(VIEWER, "m63_peer")).studying(); }

    @Test void onlyActiveWorkQualifiesAndNeitherSummaryOptInNorViewerOptInIsRequired() {
        assertThat(studying()).isFalse();
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time,end_time,notes) VALUES (?,'WORK',now()-interval '2 hours',now()-interval '1 hour','private')", PEER);
        assertThat(studying()).isFalse();
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time,notes) VALUES (?,'ROT',now(),'private')", PEER);
        assertThat(studying()).isFalse();
        jdbc.update("UPDATE time_entries SET activity_type='WORK' WHERE user_id=? AND end_time IS NULL", PEER);
        assertThat(runtime(() -> service.get(VIEWER, " M63_PEER ")).studying()).isTrue();
        jdbc.update("UPDATE time_entries SET end_time=now()+interval '1 second' WHERE user_id=? AND end_time IS NULL", PEER);
        assertThat(studying()).isFalse();
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time) VALUES (?,'WORK',now())", OTHER);
        assertThat(studying()).isFalse();
        assertThatThrownBy(() -> service.get(VIEWER, "x")).isInstanceOf(ValidationException.class);
    }

    @Test void laterReadsReauthorizePreferenceMissingSelfUnrelatedAndRemovedFriendship() {
        unavailable(VIEWER, "m63_missing"); unavailable(VIEWER, "m63_other"); unavailable(PEER, "m63_peer");
        jdbc.update("UPDATE user_preferences SET share_active_study_status=false WHERE user_id=?", PEER);
        unavailable(VIEWER, "m63_peer");
        jdbc.update("UPDATE user_preferences SET share_active_study_status=true WHERE user_id=?", PEER);
        assertThat(studying()).isFalse();
        jdbc.update("DELETE FROM user_preferences WHERE user_id=?", PEER);
        unavailable(VIEWER, "m63_peer");
        jdbc.update("INSERT INTO user_preferences(user_id,share_active_study_status) VALUES (?,true)", PEER);
        jdbc.update("DELETE FROM friendships WHERE user_low_id=? AND user_high_id=?", VIEWER, PEER);
        unavailable(VIEWER, "m63_peer");
    }

    @Test void bothPendingDirectionsAndBothBlockDirectionsFailClosedEvenForActiveWork() {
        jdbc.update("INSERT INTO time_entries(user_id,activity_type,start_time) VALUES (?,'WORK',now())", PEER);
        assertThat(studying()).isTrue();
        for (UUID requester : List.of(VIEWER, PEER)) {
            jdbc.update("UPDATE friendships SET state='PENDING',requester_id=? WHERE user_low_id=? AND user_high_id=?", requester, VIEWER, PEER);
            unavailable(VIEWER, "m63_peer");
        }
        jdbc.update("UPDATE friendships SET state='ACCEPTED' WHERE user_low_id=? AND user_high_id=?", VIEWER, PEER);
        for (UUID blocker : List.of(VIEWER, PEER)) {
            UUID blocked = blocker.equals(VIEWER) ? PEER : VIEWER;
            jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?)", blocker, blocked);
            unavailable(VIEWER, "m63_peer");
            jdbc.update("DELETE FROM user_blocks WHERE blocker_id=?", blocker);
        }
        assertThat(studying()).isTrue();
    }
}
