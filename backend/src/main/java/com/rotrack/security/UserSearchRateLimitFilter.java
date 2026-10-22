package com.rotrack.security;

import tools.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Discovery has its own subject-only budget, including invalid authenticated queries. */
public final class UserSearchRateLimitFilter extends OncePerRequestFilter {
    private final MutationRateLimiter limiter;
    private final ObjectMapper mapper;
    public UserSearchRateLimitFilter(MutationRateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!"GET".equals(request.getMethod()) || !RouteTemplates.USER_SEARCH.equals(RouteTemplates.resolve(request))
                || !(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        var decision = limiter.tryAcquire(new MutationRateKey(UUID.fromString(jwt.getToken().getSubject())));
        if (decision.allowed()) { chain.doFilter(request, response); return; }
        request.setAttribute(com.rotrack.observability.RequestLogAttributes.ERROR_CODE, "RATE_LIMITED");
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                "RATE_LIMITED", "Too many username searches", Map.of(), RouteTemplates.USER_SEARCH));
    }
}
