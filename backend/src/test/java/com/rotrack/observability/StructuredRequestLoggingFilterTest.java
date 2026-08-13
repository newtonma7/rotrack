package com.rotrack.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.web.servlet.HandlerMapping;

class StructuredRequestLoggingFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsAllowlistedCompletionFieldsWithSafeRouteAndRequestId() throws Exception {
        List<String> events = new ArrayList<>();
        StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC),
                "staging",
                "release-test",
                events::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT",
                "/api/v1/time-entries/11111111-1111-1111-1111-111111111111/stop?secret=query-sentinel"
        );
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/time-entries/{id}/stop");
        request.setAttribute(RequestLogAttributes.ERROR_CODE, "RATE_LIMITED");
        request.addHeader("Authorization", "Bearer bearer-sentinel");
        request.addHeader("Cookie", "session=cookie-sentinel");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("timestamp").asText()).isEqualTo("2026-08-08T12:00:00Z");
        assertThat(event.get("event.name").asText()).isEqualTo("http.request.completed");
        assertThat(event.get("service.name").asText()).isEqualTo("rotrack-api");
        assertThat(event.get("deployment.environment").asText()).isEqualTo("staging");
        assertThat(event.get("service.version").asText()).isEqualTo("release-test");
        assertThat(event.get("correlation.id").asText()).hasSizeGreaterThanOrEqualTo(22);
        assertThat(event.get("http.request.method").asText()).isEqualTo("PUT");
        assertThat(event.get("http.route").asText()).isEqualTo("/api/v1/time-entries/{id}/stop");
        assertThat(event.get("http.response.status_code").asInt()).isEqualTo(200);
        assertThat(event.get("http.response.status_class").asText()).isEqualTo("2xx");
        assertThat(event.get("error.code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(event.get("duration_ms").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(event.get("correlation.id").asText());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).doesNotContain(
                "bearer-sentinel",
                "cookie-sentinel",
                "query-sentinel",
                "11111111-1111-1111-1111-111111111111"
        );
    }

    @Test
    void includesStableM5ErrorCategoriesButNeverRawResourcePath() throws Exception {
        for (String code : new String[]{"INVALID_CURSOR", "TIME_ENTRY_OVERLAP", "RICH_TEXT_VERSION_CONFLICT", "IDEMPOTENCY_CONFLICT", "NOTE_DELETED"}) {
            List<String> events = new ArrayList<>();
            StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(objectMapper, Clock.systemUTC(), "staging", "release-test", events::add);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notes/11111111-1111-1111-1111-111111111111");
            request.setAttribute(RequestLogAttributes.ERROR_CODE, code);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            JsonNode event = objectMapper.readTree(events.getFirst());
            assertThat(event.get("error.code").asText()).isEqualTo(code);
            assertThat(events.getFirst()).doesNotContain("11111111-1111-1111-1111-111111111111");
        }
    }

    @Test
    void omitsUnknownUppercaseErrorCode() throws Exception {
        List<String> events = new ArrayList<>();
        StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                events::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.setAttribute(RequestLogAttributes.ERROR_CODE, "UNKNOWN_UPPERCASE_CODE");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.has("error.code")).isFalse();
    }

    @Test
    void allowlistsDashboardTemplateWithoutQueryData() throws Exception {
        List<String> events = new ArrayList<>();
        StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                events::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/dashboard/stats?timeZone=America%2FNew_York&private=sentinel"
        );

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("http.route").asText()).isEqualTo("/api/v1/dashboard/stats");
        assertThat(events.getFirst()).doesNotContain("America", "sentinel");
    }

    @Test
    void emitsOnlyAllowlistedUnexpectedExceptionCategory() throws Exception {
        List<String> events = new ArrayList<>();
        StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                events::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard/stats");
        request.setAttribute(RequestLogAttributes.EXCEPTION_TYPE, RequestLogAttributes.UNEXPECTED_SERVER_ERROR);
        request.setAttribute(RequestLogAttributes.ERROR_CODE, "INTERNAL_ERROR");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("exception.type").asText()).isEqualTo("unexpected");
        assertThat(events.getFirst()).doesNotContain("database password", "private", "RuntimeException");
    }

    @Test
    void replacesUnknownOrUnsafeRoutePatternsWithAnUnmatchedTemplate() throws Exception {
        List<String> events = new ArrayList<>();
        StructuredRequestLoggingFilter filter = new StructuredRequestLoggingFilter(
                objectMapper,
                Clock.systemUTC(),
                "staging",
                "release-test",
                events::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/private?reflection=private-sentinel");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/private");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        JsonNode event = objectMapper.readTree(events.getFirst());
        assertThat(event.get("http.route").asText()).isEqualTo("/unmatched");
        assertThat(events.getFirst()).doesNotContain("private-sentinel");
    }
}
