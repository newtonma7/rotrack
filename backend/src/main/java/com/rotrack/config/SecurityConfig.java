package com.rotrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.dto.ApiErrorResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API security — validates Supabase-issued JWTs on every protected route.
 *
 * Layer: backend config (Spring Security).
 * Auth flow: Browser sends Bearer token → JwtDecoder verifies signature and claims → controller reads sub.
 *
 * Supabase user access tokens use ES256 via JWKS. Symmetric JWT fallback is intentionally not supported;
 * accepting a shared secret in production would widen the token-forgery trust boundary.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        var authenticationEntryPoint = (org.springframework.security.web.AuthenticationEntryPoint)
                (request, response, exception) -> {
                    boolean missingCredentials = exception instanceof InsufficientAuthenticationException;
                    writeSecurityError(
                            objectMapper,
                            request.getRequestURI(),
                            response,
                            401,
                            missingCredentials ? "AUTHENTICATION_REQUIRED" : "INVALID_TOKEN",
                            missingCredentials ? "Authentication is required" : "Authentication failed"
                    );
                };
        var accessDeniedHandler = (org.springframework.security.web.access.AccessDeniedHandler)
                (request, response, exception) -> writeSecurityError(
                        objectMapper,
                        request.getRequestURI(),
                        response,
                        403,
                        "FORBIDDEN",
                        "You do not have permission to access this resource"
                );

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/readiness").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    @Bean
    OAuth2TokenValidator<Jwt> jwtValidator(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${rotrack.security.jwt.audience}") String expectedAudience
    ) {
        OAuth2TokenValidator<Jwt> issuerAndTimeValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audience -> audience != null && audience.contains(expectedAudience)
        );
        OAuth2TokenValidator<Jwt> subjectValidator = new JwtClaimValidator<String>(
                "sub",
                this::isUuid
        );
        return new DelegatingOAuth2TokenValidator<>(
                issuerAndTimeValidator,
                audienceValidator,
                subjectValidator
        );
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            OAuth2TokenValidator<Jwt> jwtValidator
    ) {
        NimbusJwtDecoder jwkDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        jwkDecoder.setJwtValidator(jwtValidator);
        return jwkDecoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private boolean isUuid(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(subject);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void writeSecurityError(
            ObjectMapper objectMapper,
            String path,
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(code, message, java.util.Map.of(), path)
        );
    }
}
