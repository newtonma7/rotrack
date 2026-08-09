package com.rotrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockFilterChain;

class MutationRateLimitFilterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsStableJson429AfterAuthenticatedMutationBudgetIsConsumed() throws Exception {
        MutationRateLimiter limiter = new MutationRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        MutationRateLimitFilter filter = new MutationRateLimitFilter(limiter, configuredObjectMapper());

        MockHttpServletResponse first = invoke(filter, request("POST", "/api/v1/time-entries/start", USER_ID), USER_ID);
        MockHttpServletResponse second = invoke(filter, request("POST", "/api/v1/time-entries/start", USER_ID), USER_ID);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
        assertThat(second.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(second.getContentAsString()).contains("\"fieldErrors\":{}");
        assertThat(second.getContentAsString()).doesNotContain("Bearer", "11111111");
    }

    @Test
    void ignoresSpoofedForwardingHeadersAndUsesTheAuthenticatedSubjectAndRoute() throws Exception {
        MutationRateLimiter limiter = new MutationRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        MutationRateLimitFilter filter = new MutationRateLimitFilter(limiter, configuredObjectMapper());

        MockHttpServletRequest firstRequest = request("POST", "/api/v1/time-entries/start", USER_ID);
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse first = invoke(filter, firstRequest, USER_ID);
        MockHttpServletRequest spoofedRequest = request("POST", "/api/v1/time-entries/start", USER_ID);
        spoofedRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        MockHttpServletResponse second = invoke(filter, spoofedRequest, USER_ID);
        MockHttpServletResponse differentUser = invoke(
                filter,
                request("POST", "/api/v1/time-entries/start", OTHER_USER_ID),
                OTHER_USER_ID
        );
        MockHttpServletResponse differentRoute = invoke(
                filter,
                request("PUT", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222/stop", USER_ID),
                USER_ID
        );

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(differentUser.getStatus()).isEqualTo(200);
        assertThat(differentRoute.getStatus()).isEqualTo(429);
    }

    @Test
    void startAndStopShareOneAuthenticatedUserBudget() throws Exception {
        MutationRateLimiter limiter = new MutationRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        MutationRateLimitFilter filter = new MutationRateLimitFilter(limiter, configuredObjectMapper());

        MockHttpServletResponse start = invoke(
                filter,
                request("POST", "/api/v1/time-entries/start", USER_ID),
                USER_ID
        );
        MockHttpServletResponse stop = invoke(
                filter,
                request("PUT", "/api/v1/time-entries/33333333-3333-3333-3333-333333333333/stop", USER_ID),
                USER_ID
        );

        assertThat(start.getStatus()).isEqualTo(200);
        assertThat(stop.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitUnauthenticatedOrNonMutationRequests() throws Exception {
        MutationRateLimiter limiter = new MutationRateLimiter(1, Duration.ofMinutes(1), 10, java.time.Clock.systemUTC());
        MutationRateLimitFilter filter = new MutationRateLimitFilter(limiter, configuredObjectMapper());

        MockHttpServletResponse unauthenticated = invokeWithoutAuthentication(
                filter,
                new MockHttpServletRequest("POST", "/api/v1/time-entries/start")
        );
        MockHttpServletResponse read = invoke(
                filter,
                request("GET", "/api/v1/time-entries/active", USER_ID),
                USER_ID
        );

        assertThat(unauthenticated.getStatus()).isEqualTo(200);
        assertThat(read.getStatus()).isEqualTo(200);
    }

    private ObjectMapper configuredObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private MockHttpServletResponse invoke(
            MutationRateLimitFilter filter,
            MockHttpServletRequest request,
            UUID userId
    ) throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .claim("aud", "authenticated")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse invokeWithoutAuthentication(
            MutationRateLimitFilter filter,
            MockHttpServletRequest request
    ) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String method, String path, UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
