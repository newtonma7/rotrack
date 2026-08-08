package com.rotrack.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rotrack.controller.TimeEntryController;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.service.TimeEntryService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the production decoder and bearer-token filter with ephemeral keys.
 * The local JWKS endpoint exposes only a generated public key; no reusable token
 * or secret key leaves this test process.
 */
@WebMvcTest(TimeEntryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SignedJwtSecurityFilterTest {

    private static final String ISSUER = "https://issuer.example.test/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static final String EC_KEY_ID = "generated-ec-test-key";
    private static final String RSA_KEY_ID = "generated-rsa-test-key";
    private static final KeyPair TRUSTED_EC_KEY = generateKeyPair("EC", 256);
    private static final KeyPair UNTRUSTED_EC_KEY = generateKeyPair("EC", 256);
    private static final KeyPair RSA_KEY = generateKeyPair("RSA", 2048);

    private static HttpServer jwksServer;
    private static String jwksUri;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    static void startJwksServer() throws IOException {
        ECKey publicEcJwk = new ECKey.Builder(Curve.P_256, (ECPublicKey) TRUSTED_EC_KEY.getPublic())
                .keyID(EC_KEY_ID)
                .algorithm(JWSAlgorithm.ES256)
                .build();
        RSAKey publicRsaJwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) RSA_KEY.getPublic())
                .keyID(RSA_KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        byte[] response = new JWKSet(java.util.List.of(publicEcJwk, publicRsaJwk))
                .toString()
                .getBytes(StandardCharsets.UTF_8);
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

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        try {
            startJwksServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start the local JWKS endpoint", exception);
        }
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUri);
        registry.add("SUPABASE_JWT_AUDIENCE", () -> AUDIENCE);
    }

    @Test
    void acceptsValidSignedToken() throws Exception {
        String token = es256Token(TRUSTED_EC_KEY, ISSUER, AUDIENCE, UUID.randomUUID().toString(),
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300));

        mockMvc.perform(get("/api/v1/time-entries/active").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsTokenWithBadSignatureUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(UNTRUSTED_EC_KEY, ISSUER, AUDIENCE, UUID.randomUUID().toString(),
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300)));
    }

    @Test
    void rejectsUnsupportedSigningAlgorithmUsingStableEnvelope() throws Exception {
        JWTClaimsSet claims = claims(ISSUER, AUDIENCE, UUID.randomUUID().toString(),
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300));
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY_ID).build(),
                claims
        );
        jwt.sign(new RSASSASigner(RSA_KEY.getPrivate()));

        assertInvalidToken(jwt.serialize());
    }

    @Test
    void rejectsWrongIssuerUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, "https://wrong.example.test/auth/v1", AUDIENCE,
                UUID.randomUUID().toString(), Instant.now().minusSeconds(30), Instant.now().plusSeconds(300)));
    }

    @Test
    void rejectsWrongAudienceUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, ISSUER, "wrong-audience", UUID.randomUUID().toString(),
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300)));
    }

    @Test
    void rejectsExpiredTokenUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, ISSUER, AUDIENCE, UUID.randomUUID().toString(),
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300)));
    }

    @Test
    void rejectsNotBeforeTokenUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, ISSUER, AUDIENCE, UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(600)));
    }

    @Test
    void rejectsMissingSubjectUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, ISSUER, AUDIENCE, null,
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300)));
    }

    @Test
    void rejectsNonUuidSubjectUsingStableEnvelope() throws Exception {
        assertInvalidToken(es256Token(TRUSTED_EC_KEY, ISSUER, AUDIENCE, "not-a-uuid",
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300)));
    }

    private void assertInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/api/v1/time-entries/active").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.message").value("Authentication failed"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/v1/time-entries/active"));
    }

    private static String es256Token(
            KeyPair keyPair,
            String issuer,
            String audience,
            String subject,
            Instant notBefore,
            Instant expiresAt
    ) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(EC_KEY_ID).build(),
                claims(issuer, audience, subject, notBefore, expiresAt)
        );
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static JWTClaimsSet claims(
            String issuer,
            String audience,
            String subject,
            Instant notBefore,
            Instant expiresAt
    ) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .notBeforeTime(Date.from(notBefore))
                .expirationTime(Date.from(expiresAt));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    private static KeyPair generateKeyPair(String algorithm, int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
            generator.initialize(keySize);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create an ephemeral test key", exception);
        }
    }
}
