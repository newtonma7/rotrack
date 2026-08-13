package com.rotrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.dto.ApiErrorResponse;
import com.rotrack.web.RouteTemplates;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public final class NoteRateLimitFilter extends OncePerRequestFilter {
    private final NoteRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public NoteRateLimitFilter(NoteRateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!RouteTemplates.isNoteMutation(request)) {
            chain.doFilter(request, response);
            return;
        }
        UUID subject = authenticatedSubject();
        if (subject == null) {
            chain.doFilter(request, response);
            return;
        }
        MutationRateLimiter.Decision decision = limiter.tryAcquire(subject);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(com.rotrack.observability.RequestLogAttributes.ERROR_CODE, "RATE_LIMITED");
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                "RATE_LIMITED", "Too many rich-text save requests", Map.of(), RouteTemplates.resolve(request)));
    }

    private UUID authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) return null;
        try {
            return UUID.fromString(jwt.getToken().getSubject());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
