package com.rotrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.dto.ApiErrorResponse;
import com.rotrack.web.RouteTemplates;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the mutation limit after Spring has authenticated the bearer token. The JWT subject is
 * the only identity input; forwarding headers are intentionally ignored because this API does not
 * have a configured trusted-proxy identity boundary.
 */
public final class MutationRateLimitFilter extends OncePerRequestFilter {

    private final MutationRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public MutationRateLimitFilter(MutationRateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var route = RouteTemplates.mutation(request);
        UUID subject = authenticatedSubject();
        if (route.isEmpty() || RouteTemplates.isNoteMutation(request) || subject == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Start and stop deliberately share one subject-only budget; alternating routes must not bypass it.
        MutationRateLimiter.Decision decision = limiter.tryAcquire(new MutationRateKey(subject));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        request.setAttribute(com.rotrack.observability.RequestLogAttributes.ERROR_CODE, "RATE_LIMITED");
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        "RATE_LIMITED",
                        "Too many mutation requests",
                        Map.of(),
                        RouteTemplates.resolve(request)
                )
        );
    }

    private UUID authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
