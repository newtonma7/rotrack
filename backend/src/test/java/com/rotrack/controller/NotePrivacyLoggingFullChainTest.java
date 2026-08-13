package com.rotrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rotrack.config.NotesConfiguration;
import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.NoteDeletedException;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.NoteDTO;
import com.rotrack.dto.TimeEntryDTO;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.model.ActivityType;
import com.rotrack.observability.StructuredRequestLoggingFilter;
import com.rotrack.service.NoteService;
import com.rotrack.service.TimeEntryService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the generated-JWT bearer path, controller mappings, note/rate-limit filters, and
 * completion logger together. Runtime-only opaque values make accidental log/error inclusion fail
 * without committing private-looking fixtures or recording their values in test output.
 */
@WebMvcTest(controllers = {NoteController.class, TimeEntryController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class, NotesConfiguration.class})
class NotePrivacyLoggingFullChainTest {

    private static final String ISSUER = "https://issuer.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String KEY_ID = "generated-note-privacy-key";
    private static final KeyPair SIGNING_KEY = generateKeyPair();
    private static HttpServer jwksServer;
    private static String jwksUri;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private NoteService noteService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private TimeEntryService timeEntryService;

    private MockMvc mockMvc;
    private List<String> completionEvents;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        try {
            startJwksServer();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start the local JWKS endpoint", exception);
        }
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUri);
        registry.add("SUPABASE_JWT_AUDIENCE", () -> AUDIENCE);
        registry.add("rotrack.notes.writes-enabled", () -> "false");
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        completionEvents = new ArrayList<>();
        StructuredRequestLoggingFilter loggingFilter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                completionEvents::add
        );
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(loggingFilter)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void replayErrorsOmitPrivateSentinelsFromResponsesAndCompletionLogs() throws Exception {
        String bearerSentinel = opaque();
        MvcResult unauthorized = mockMvc.perform(get("/api/v1/notes")
                        .header("Authorization", "Bearer " + bearerSentinel))
                .andExpect(status().isUnauthorized())
                .andReturn();

        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID timeEntryId = UUID.randomUUID();
        String token = signedToken(userId);
        String titleSentinel = opaque();
        String contentSentinel = opaque();
        String safeLinkSentinel = opaque();
        String requestBodySentinel = opaque();
        String privatePayloadSentinel = opaque();
        String safeLinkUrl = "https://example.test/private/" + safeLinkSentinel;
        String conflictKey = UUID.randomUUID().toString();
        String deletedKey = UUID.randomUUID().toString();
        JsonNode content = noteContent(contentSentinel, requestBodySentinel + " " + privatePayloadSentinel, safeLinkUrl);
        String body = noteBody(titleSentinel, content, timeEntryId);
        String contaminatedMessage = String.join(" ", titleSentinel, contentSentinel, safeLinkUrl,
                requestBodySentinel, privatePayloadSentinel, userId.toString(), noteId.toString(),
                timeEntryId.toString(), conflictKey, deletedKey, bearerSentinel, token);
        when(noteService.create(any(), any(), any()))
                .thenThrow(new ConflictException("IDEMPOTENCY_CONFLICT", contaminatedMessage))
                .thenThrow(new NoteDeletedException());

        MvcResult replayConflict = mockMvc.perform(post("/api/v1/notes")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andReturn();
        MvcResult deletedReplay = mockMvc.perform(post("/api/v1/notes")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", deletedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isGone())
                .andReturn();

        List<String> privateValues = List.of(
                bearerSentinel, token, titleSentinel, contentSentinel, safeLinkSentinel, safeLinkUrl,
                requestBodySentinel, privatePayloadSentinel, userId.toString(), noteId.toString(),
                timeEntryId.toString(), conflictKey, deletedKey
        );
        for (MvcResult result : List.of(unauthorized, replayConflict, deletedReplay)) {
            String response = result.getResponse().getContentAsString();
            for (String value : privateValues) assertThat(response).doesNotContain(value);
        }
        assertThat(completionEvents).hasSize(3);
        for (String event : completionEvents) {
            for (String value : privateValues) assertThat(event).doesNotContain(value);
        }
    }

    @Test
    void privateNoteAndSessionValuesStayOutOfCompletionLogsAndErrorResponses() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID timeEntryId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        String token = signedToken(userId);
        String titleSentinel = opaque();
        String richContentSentinel = opaque();
        String safeLinkSentinel = opaque();
        String requestBodySentinel = opaque();
        String sessionLabelSentinel = opaque();
        String safeLinkUrl = "https://example.test/opaque/" + safeLinkSentinel;

        TimeEntryDTO started = new TimeEntryDTO(
                timeEntryId,
                ActivityType.WORK,
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                sessionLabelSentinel
        );
        when(timeEntryService.startSession(userId, ActivityType.WORK, sessionLabelSentinel)).thenReturn(started);

        JsonNode content = noteContent(richContentSentinel, requestBodySentinel, safeLinkUrl);
        String noteBody = noteBody(titleSentinel, content, timeEntryId);
        NoteDTO created = new NoteDTO(
                noteId,
                titleSentinel,
                richContentSentinel,
                timeEntryId,
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"),
                content,
                richContentSentinel + " " + requestBodySentinel,
                1
        );
        when(noteService.create(any(), any(), any())).thenReturn(new NoteService.CreateResult(created, false));
        when(noteService.get(userId, noteId)).thenThrow(new RuntimeException(
                titleSentinel + " " + richContentSentinel + " " + safeLinkUrl + " "
                        + requestBodySentinel + " " + sessionLabelSentinel + " " + timeEntryId + " " + noteId
        ));

        mockMvc.perform(post("/api/v1/time-entries/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WORK\",\"notes\":\"" + sessionLabelSentinel + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(timeEntryId.toString()));

        mockMvc.perform(post("/api/v1/notes")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(noteId.toString()));

        MvcResult error = mockMvc.perform(get("/api/v1/notes/{id}", noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/v1/notes/{id}"))
                .andReturn();

        List<String> privateValues = List.of(
                titleSentinel,
                richContentSentinel,
                safeLinkSentinel,
                safeLinkUrl,
                requestBodySentinel,
                sessionLabelSentinel,
                timeEntryId.toString(),
                noteId.toString(),
                token
        );
        assertThat(completionEvents).hasSize(3);
        assertThat(completionEvents).allSatisfy(event -> privateValues.forEach(value ->
                assertThat(event).doesNotContain(value)));
        assertEvent(completionEvents.get(0), 201, "2xx", "/api/v1/time-entries/start", null, null);
        assertEvent(completionEvents.get(1), 201, "2xx", "/api/v1/notes", null, null);
        assertEvent(completionEvents.get(2), 500, "5xx", "/api/v1/notes/{id}", "INTERNAL_ERROR", "unexpected");

        JsonNode errorBody = objectMapper.readTree(error.getResponse().getContentAsString());
        assertThat(errorBody.get("error").get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.getResponse().getContentAsString()).doesNotContain(
                titleSentinel, richContentSentinel, safeLinkUrl, requestBodySentinel,
                sessionLabelSentinel, timeEntryId.toString(), noteId.toString()
        );
        assertThat(error.getResponse().getHeader("X-Request-ID"))
                .isEqualTo(objectMapper.readTree(completionEvents.get(2)).get("correlation.id").asText());
    }

    private void assertEvent(
            String serialized,
            int status,
            String statusClass,
            String route,
            String errorCode,
            String exceptionType
    ) throws Exception {
        JsonNode event = objectMapper.readTree(serialized);
        assertThat(event.get("event.name").asText()).isEqualTo("http.request.completed");
        assertThat(event.get("http.response.status_code").asInt()).isEqualTo(status);
        assertThat(event.get("http.response.status_class").asText()).isEqualTo(statusClass);
        assertThat(event.get("http.route").asText()).isEqualTo(route);
        if (errorCode == null) {
            assertThat(event.has("error.code")).isFalse();
        } else {
            assertThat(event.get("error.code").asText()).isEqualTo(errorCode);
        }
        if (exceptionType == null) {
            assertThat(event.has("exception.type")).isFalse();
        } else {
            assertThat(event.get("exception.type").asText()).isEqualTo(exceptionType);
        }
    }

    private String noteBody(String title, JsonNode content, UUID timeEntryId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        body.set("contentJson", content);
        if (timeEntryId == null) body.putNull("timeEntryId");
        else body.put("timeEntryId", timeEntryId.toString());
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode noteContent(String richContent, String bodySentinel, String linkUrl) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        ObjectNode document = root.putObject("document");
        document.put("type", "doc");
        ArrayNode blocks = document.putArray("content");
        ObjectNode paragraph = blocks.addObject();
        paragraph.put("type", "paragraph");
        ArrayNode textNodes = paragraph.putArray("content");
        ObjectNode text = textNodes.addObject();
        text.put("type", "text");
        text.put("text", richContent + " " + bodySentinel);
        ArrayNode marks = text.putArray("marks");
        ObjectNode link = marks.addObject();
        link.put("type", "link");
        link.putObject("attrs").put("href", linkUrl);
        return root;
    }

    private String signedToken(UUID subject) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY_ID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .audience(AUDIENCE)
                        .subject(subject.toString())
                        .issueTime(Date.from(now.minusSeconds(30)))
                        .notBeforeTime(Date.from(now.minusSeconds(30)))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build()
        );
        jwt.sign(new ECDSASigner((ECPrivateKey) SIGNING_KEY.getPrivate()));
        return jwt.serialize();
    }

    private static void startJwksServer() throws Exception {
        if (jwksServer != null) return;
        ECKey publicKey = new ECKey.Builder(Curve.P_256, (ECPublicKey) SIGNING_KEY.getPublic())
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.ES256)
                .build();
        byte[] response = new JWKSet(List.of(publicKey)).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        jwksServer.start();
        jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create an ephemeral test key", exception);
        }
    }

    private static String opaque() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
