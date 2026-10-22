package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rotrack.config.DatabaseTlsValidator;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.InvalidCursorException;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.exception.ValidationException;
import com.rotrack.model.FriendshipPair;
import com.rotrack.repository.FriendshipRepository;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class FriendshipServicePostgresIntegrationTest {
    private static final UUID A = UUID.fromString("10000000-0062-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("90000000-0062-4000-8000-000000000002");
    private static final UUID C = UUID.fromString("f0000000-0062-4000-8000-000000000003");
    @Autowired FriendshipService service;
    @Autowired FriendshipRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
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

    @BeforeAll
    void runtimeProbeRole() {
        // The CI migration role is intentionally NOBYPASSRLS; production API setup separately
        // supplies BYPASSRLS and profile reads. Inherit only migration-owned DML for this probe.
        jdbc.execute("CREATE ROLE rotrack_m61_api_probe NOLOGIN BYPASSRLS IN ROLE rotrack_runtime");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO rotrack_m61_api_probe");
        jdbc.execute("GRANT SELECT(id,username) ON users TO rotrack_m61_api_probe");
    }

    @AfterAll
    void removeProbeRole() {
        jdbc.execute("DROP OWNED BY rotrack_m61_api_probe");
        jdbc.execute("DROP ROLE rotrack_m61_api_probe");
    }

    @BeforeEach
    void fixtures() {
        cleanup();
        addUser(A, "m61_alice"); addUser(B, "m61_bob"); addUser(C, "m61_carol");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM auth.users WHERE id IN (?, ?, ?)", A, B, C);
        jdbc.update("DELETE FROM auth.users WHERE email LIKE 'm61-page-%@example.test'");
    }

    private void addUser(UUID id, String username) {
        jdbc.update("INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES (?, ?, jsonb_build_object('username', ?))",
                id, "m61-page-" + id + "@example.test", username);
    }

    // Exercise the real service with the exact API role's column privileges, not superuser writes.
    private <T> T runtime(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> {
            jdbc.execute("SET LOCAL ROLE rotrack_m61_api_probe");
            return action.get();
        });
    }

    @Test
    void transitionsAreOwnedExplicitAndRetrySafe() {
        var request = runtime(() -> service.request(A, " M61_BOB "));
        assertThat(runtime(() -> service.requests(B, "incoming", null)).items()).containsExactly(requestFor(request.id(), "m61_alice"));
        assertThat(runtime(() -> service.requests(A, "outgoing", null)).items()).containsExactly(request);
        assertThatThrownBy(() -> runtime(() -> service.request(B, "m61_alice"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> runtime(() -> service.accept(A, request.id()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> runtime(() -> service.accept(C, request.id()))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(runtime(() -> service.accept(B, request.id())).username()).isEqualTo("m61_alice");
        assertThat(runtime(() -> service.accept(B, request.id())).id()).isEqualTo(request.id());
        assertThat(runtime(() -> service.friends(A, null)).items()).containsExactly(request);
        assertThatThrownBy(() -> runtime(() -> { service.deleteRequest(A, request.id()); return null; })).isInstanceOf(ConflictException.class);
        runtime(() -> { service.removeFriend(B, request.id()); return null; });
        assertThat(runtime(() -> service.friends(A, null)).items()).isEmpty();
        var second = runtime(() -> service.request(B, "m61_alice"));
        runtime(() -> { service.deleteRequest(A, second.id()); return null; });
        var third = runtime(() -> service.request(A, "m61_bob"));
        runtime(() -> { service.deleteRequest(A, third.id()); return null; });
        assertThatThrownBy(() -> runtime(() -> service.accept(B, third.id()))).isInstanceOf(ResourceNotFoundException.class);
    }

    private com.rotrack.dto.FriendshipDTO.Relationship requestFor(UUID id, String name) {
        return new com.rotrack.dto.FriendshipDTO.Relationship(id, name);
    }

    @Test
    void blocksAreDirectionalAndAtomicallyEraseRelationships() {
        var request = runtime(() -> service.request(A, "m61_bob"));
        runtime(() -> { service.block(B, "m61_alice"); service.block(B, "m61_alice"); return null; });
        assertThatThrownBy(() -> runtime(() -> service.accept(B, request.id()))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> runtime(() -> service.request(A, "m61_bob"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> runtime(() -> service.request(A, "not_here"))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(runtime(() -> service.search(A, "m61")).items()).extracting("username").containsExactly("m61_carol");
        runtime(() -> { service.block(A, "m61_bob"); service.unblock(B, "m61_alice"); return null; });
        assertThatThrownBy(() -> runtime(() -> service.request(B, "m61_alice"))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(runtime(() -> service.blocks(B, null)).items()).isEmpty();
        assertThat(runtime(() -> service.blocks(A, null)).items()).extracting("username").containsExactly("m61_bob");
        runtime(() -> { service.unblock(A, "m61_bob"); service.unblock(A, "not_here"); return null; });
        assertThat(runtime(() -> service.friends(A, null)).items()).isEmpty();
        var next = runtime(() -> service.request(A, "m61_bob"));
        runtime(() -> service.accept(B, next.id()));
        runtime(() -> { service.block(A, "m61_bob"); return null; });
        assertThat(runtime(() -> service.friends(B, null)).items()).isEmpty();
        assertThatThrownBy(() -> runtime(() -> service.request(A, "m61_alice"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> runtime(() -> { service.block(A, "m61_alice"); return null; })).isInstanceOf(ValidationException.class);
    }

    @Test
    void searchIsLiteralBoundedAndReportsOnlyViewerRelationship() {
        addUser(UUID.randomUUID(), "m61xbob");
        assertThat(runtime(() -> service.search(A, "M61_")).items()).extracting("username").containsExactly("m61_bob", "m61_carol");
        runtime(() -> service.request(A, "m61_bob"));
        runtime(() -> service.request(C, "m61_alice"));
        assertThat(runtime(() -> service.search(A, "m61")).items()).extracting("relationship").containsExactly(
                com.rotrack.dto.FriendshipDTO.RelationshipState.OUTGOING,
                com.rotrack.dto.FriendshipDTO.RelationshipState.INCOMING,
                com.rotrack.dto.FriendshipDTO.RelationshipState.NONE);
        for (String invalid : List.of("ab", "%bob", "abc!", "a".repeat(25))) {
            assertThatThrownBy(() -> runtime(() -> service.search(A, invalid))).isInstanceOf(ValidationException.class);
        }
        for (int i = 0; i < 23; i++) addUser(UUID.randomUUID(), "page_" + String.format("%02d", i));
        assertThat(runtime(() -> service.search(A, "page")).items()).hasSize(10);
    }

    @Test
    void listsUseStableKeysetPagesAndValidateCursors() {
        for (int i = 0; i < 23; i++) {
            String name = "page_" + String.format("%02d", i);
            addUser(UUID.randomUUID(), name);
            runtime(() -> service.request(A, name));
            runtime(() -> { service.block(B, name); return null; });
        }
        var first = runtime(() -> service.requests(A, "outgoing", null));
        assertThat(first.items()).hasSize(20);
        var second = runtime(() -> service.requests(A, "outgoing", first.nextCursor()));
        assertThat(second.items()).hasSize(3).doesNotContainAnyElementsOf(first.items());
        assertThat(second.nextCursor()).isNull();
        var blocks = runtime(() -> service.blocks(B, null));
        assertThat(blocks.items()).hasSize(20);
        assertThat(runtime(() -> service.blocks(B, blocks.nextCursor())).items()).hasSize(3).doesNotContainAnyElementsOf(blocks.items());
        for (String invalid : List.of("", "bad", "a".repeat(500))) {
            assertThatThrownBy(() -> runtime(() -> service.friends(A, invalid))).isInstanceOf(InvalidCursorException.class);
        }
        assertThatThrownBy(() -> runtime(() -> service.requests(A, "other", null))).isInstanceOf(ValidationException.class);
    }

    @Test
    void reversedConcurrentRequestsNeverImplicitlyAccept() throws Exception {
        var outcomes = race(() -> service.request(A, "m61_bob"), () -> service.request(B, "m61_alice"));
        assertThat(outcomes.stream().filter("ok"::equals)).hasSize(1);
        assertThat(outcomes.stream().filter("conflict"::equals)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT state FROM friendships WHERE user_low_id=? AND user_high_id=?", String.class, A, B)).isEqualTo("PENDING");
    }

    @Test
    void requestAndAcceptRacingBlocksCannotResurrectPair() throws Exception {
        race(() -> service.request(A, "m61_bob"), () -> { service.block(B, "m61_alice"); return null; });
        assertNoRelationship();
        runtime(() -> { service.unblock(B, "m61_alice"); return null; });
        var request = runtime(() -> service.request(A, "m61_bob"));
        race(() -> service.accept(B, request.id()), () -> { service.block(A, "m61_bob"); return null; });
        assertNoRelationship();
        assertThatThrownBy(() -> runtime(() -> service.accept(B, request.id()))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reciprocalConcurrentBlocksSurviveIndependentUnblock() throws Exception {
        race(() -> { service.block(A, "m61_bob"); return null; }, () -> { service.block(B, "m61_alice"); return null; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_blocks WHERE blocker_id IN (?, ?)", Integer.class, A, B)).isEqualTo(2);
        runtime(() -> { service.unblock(A, "m61_bob"); return null; });
        assertThatThrownBy(() -> runtime(() -> service.request(A, "m61_bob"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestWaitsForPairLockBeforeCheckingEligibilityOrWriting() throws Exception {
        assertWaitsForCommittedBlock(() -> service.request(A, "m61_bob"));
    }

    @Test
    void acceptWaitsForPairLockBeforeAnyRowWriteAndReReadsAfterCleanup() throws Exception {
        var request = runtime(() -> service.request(A, "m61_bob"));
        assertWaitsForCommittedBlock(() -> service.accept(B, request.id()));
    }

    private void assertWaitsForCommittedBlock(Supplier<?> contender) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = runtime(() -> {
                var pair = FriendshipPair.of(A, B);
                repository.lock(pair);
                repository.block(B, A);
                repository.deletePair(pair);
                var submitted = pool.submit(() -> outcome(contender));
                await().atMost(Duration.ofSeconds(5)).until(() ->
                        jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted", Integer.class) > 0);
                assertNoRelationship();
                return submitted;
            });
            assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("not_found");
        }
        assertNoRelationship();
    }

    private void assertNoRelationship() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendships WHERE user_low_id=? AND user_high_id=?", Integer.class, A, B)).isZero();
    }

    private List<String> race(Supplier<?> left, Supplier<?> right) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> a = () -> { start.await(5, TimeUnit.SECONDS); return outcome(left); };
            Callable<String> b = () -> { start.await(5, TimeUnit.SECONDS); return outcome(right); };
            var first = pool.submit(a); var second = pool.submit(b);
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private String outcome(Supplier<?> action) {
        try { runtime(action); return "ok"; }
        catch (ConflictException exception) { return "conflict"; }
        catch (ResourceNotFoundException exception) { return "not_found"; }
    }
}
