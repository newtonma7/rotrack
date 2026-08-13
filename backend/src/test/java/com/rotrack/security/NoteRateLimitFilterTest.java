package com.rotrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class NoteRateLimitFilterTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void noteBudgetIsSeparateAndReturnsRetryAfter() throws Exception {
        NoteRateLimiter limiter = new NoteRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        NoteRateLimitFilter filter = new NoteRateLimitFilter(limiter, new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse first = invoke(filter, "POST", "/api/v1/notes");
        MockHttpServletResponse second = invoke(filter, "PUT", "/api/v1/notes/22222222-2222-4222-8222-222222222222");
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void readsAndTimerMutationsDoNotConsumeNoteBudget() throws Exception {
        NoteRateLimiter limiter = new NoteRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        NoteRateLimitFilter filter = new NoteRateLimitFilter(limiter, new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse read = invoke(filter, "GET", "/api/v1/notes");
        MockHttpServletResponse timer = invoke(filter, "POST", "/api/v1/time-entries/start");
        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(timer.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse invoke(NoteRateLimitFilter filter, String method, String path) throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "ES256").subject(USER.toString())
                .claim("aud", "authenticated").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
