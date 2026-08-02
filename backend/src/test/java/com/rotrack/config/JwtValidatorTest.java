package com.rotrack.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rejectsExpiredToken() {
        assertFalse(validator.validate(token(ISSUER, List.of("authenticated"), UUID.randomUUID().toString(),
                Instant.now().minusSeconds(60))).getErrors().isEmpty());
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
