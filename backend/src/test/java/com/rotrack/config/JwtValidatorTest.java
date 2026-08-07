package com.rotrack.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidatorTest {

    private static final String ISSUER = "https://example.test/issuer";
    private OAuth2TokenValidator<Jwt> validator;

    @BeforeEach
    void setUp() {
        validator = new SecurityConfig().jwtValidator(ISSUER, "authenticated");
    }

    @Test
    void acceptsValidIssuerAudienceSubjectAndTimeClaims() {
        assertTrue(validator.validate(token(ISSUER, List.of("authenticated"), UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60))).getErrors().isEmpty());
    }

    @Test
    void rejectsWrongIssuer() {
        assertFalse(validator.validate(token("https://wrong.test", List.of("authenticated"),
                UUID.randomUUID().toString(), Instant.now().plusSeconds(60))).getErrors().isEmpty());
    }

    @Test
    void rejectsWrongAudience() {
        assertFalse(validator.validate(token(ISSUER, List.of("wrong-audience"), UUID.randomUUID().toString(),
                Instant.now().plusSeconds(60))).getErrors().isEmpty());
    }

    @Test
    void rejectsNonUuidSubject() {
        assertFalse(validator.validate(token(ISSUER, List.of("authenticated"), "auth-user-123",
                Instant.now().plusSeconds(60))).getErrors().isEmpty());
    }

    @Test
    void rejectsCryptographicallyInvalidSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair trustedKey = generator.generateKeyPair();
        KeyPair attackerKey = generator.generateKeyPair();
        String token = signedToken(attackerKey, UUID.randomUUID().toString());
        SignedJWT parsed = SignedJWT.parse(token);

        assertFalse(parsed.verify(new ECDSAVerifier(
                (java.security.interfaces.ECPublicKey) trustedKey.getPublic()
        )));
    }

    @Test
    void rejectsExpiredToken() {
        assertFalse(validator.validate(token(ISSUER, List.of("authenticated"), UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60))).getErrors().isEmpty());
    }

    private String signedToken(KeyPair keyPair, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("authenticated")
                .subject(subject)
                .issueTime(java.util.Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).build(), claims);
        jwt.sign(new ECDSASigner((java.security.interfaces.ECPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private Jwt token(String issuer, List<String> audience, String subject, Instant expiresAt) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(expiresAt)
                .build();
    }
}
