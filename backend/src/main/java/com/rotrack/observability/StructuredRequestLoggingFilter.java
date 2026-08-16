package com.rotrack.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.web.RouteTemplates;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits one allowlisted JSON completion event. It deliberately never reads request headers, query
 * values, bodies, principals, or exceptions, which makes omission the default redaction policy.
 */
public final class StructuredRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredRequestLoggingFilter.class);
    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger("com.rotrack.observability.StructuredRequestLoggingFallback");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
    );
    private static final Set<String> ERROR_CODES = Set.of(
            "ACTIVE_SESSION_EXISTS",
            "AUTHENTICATION_REQUIRED",
            "BAD_REQUEST",
            "CONFLICT",
            "CONSTRAINT_VIOLATION",
            "FORBIDDEN",
            "INTERNAL_ERROR",
            "INVALID_PARAMETER",
            "INVALID_CURSOR",
            "INVALID_TOKEN",
            "MALFORMED_JSON",
            "NOT_FOUND",
            "RATE_LIMITED",
            "RICH_TEXT_VERSION_CONFLICT",
            "IDEMPOTENCY_CONFLICT",
            "NOTE_DELETED",
            "PAYLOAD_TOO_LARGE",
            "TIME_ENTRY_OVERLAP",
            "TIME_ENTRY_NOT_FOUND",
            "VALIDATION_ERROR"
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String environment;
    private final String serviceVersion;
    private final Consumer<String> sink;

    public StructuredRequestLoggingFilter(
            ObjectMapper objectMapper,
            Clock clock,
            String environment,
            String serviceVersion,
            Consumer<String> sink
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.environment = environment;
        this.serviceVersion = serviceVersion;
        this.sink = sink;
    }

    public static StructuredRequestLoggingFilter production(
            ObjectMapper objectMapper,
            Clock clock,
            String environment,
            String serviceVersion
    ) {
        return new StructuredRequestLoggingFilter(
                objectMapper,
                clock,
                environment,
                serviceVersion,
                message -> LOGGER.info("{}", message)
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = newRequestId();
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedNanos = Math.max(0, System.nanoTime() - startedAt);
            emit(request, response, requestId, elapsedNanos / 1_000_000);
        }
    }

    private void emit(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            long durationMillis
    ) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now(clock).toString());
        event.put("level", "INFO");
        event.put("service.name", "rotrack-api");
        event.put("deployment.environment", environment);
        event.put("service.version", serviceVersion);
        event.put("event.name", "http.request.completed");
        event.put("correlation.id", requestId);
        event.put("http.request.method", safeMethod(request.getMethod()));
        event.put("http.route", RouteTemplates.resolve(request));
        event.put("http.response.status_code", response.getStatus());
        event.put("http.response.status_class", statusClass(response.getStatus()));
        event.put("duration_ms", durationMillis);
        String errorCode = safeErrorCode(request.getAttribute(RequestLogAttributes.ERROR_CODE));
        if (errorCode != null) {
            event.put("error.code", errorCode);
        }
        String exceptionType = safeExceptionType(request.getAttribute(RequestLogAttributes.EXCEPTION_TYPE));
        if (exceptionType != null) {
            event.put("exception.type", exceptionType);
        }
        try {
            sink.accept(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException exception) {
            // The map contains only primitives and constants; this is a last-resort safe failure.
            FALLBACK_LOGGER.error("Structured request event serialization failed");
        }
    }

    private String safeExceptionType(Object value) {
        return RequestLogAttributes.UNEXPECTED_SERVER_ERROR.equals(value)
                ? RequestLogAttributes.UNEXPECTED_SERVER_ERROR
                : null;
    }

    private String safeErrorCode(Object value) {
        if (value instanceof String code && ERROR_CODES.contains(code)) {
            return code;
        }
        return null;
    }

    private String safeMethod(String method) {
        return HTTP_METHODS.contains(method) ? method : "OTHER";
    }

    private String statusClass(int status) {
        if (status >= 200 && status < 300) {
            return "2xx";
        }
        if (status >= 300 && status < 400) {
            return "3xx";
        }
        if (status >= 400 && status < 500) {
            return "4xx";
        }
        return "5xx";
    }

    private String newRequestId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
