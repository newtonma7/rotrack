package com.rotrack.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Keeps operational route labels separate from request URLs. Raw paths can contain resource IDs
 * and query data, so security and logging use this small allowlist instead of user input.
 */
public final class RouteTemplates {

    public static final String HEALTH = "/api/v1/health";
    public static final String READINESS = "/api/v1/readiness";
    public static final String START = "/api/v1/time-entries/start";
    public static final String COLLECTION = "/api/v1/time-entries";
    public static final String HISTORY = "/api/v1/time-entries/history";
    public static final String ACTIVE = "/api/v1/time-entries/active";
    public static final String ENTRY = "/api/v1/time-entries/{id}";
    public static final String STOP = "/api/v1/time-entries/{id}/stop";
    public static final String DASHBOARD_STATS = "/api/v1/dashboard/stats";
    public static final String PREFERENCES = "/api/v1/preferences";
    public static final String NOTES = "/api/v1/notes";
    public static final String NOTE = "/api/v1/notes/{id}";
    private static final String UNMATCHED = "/unmatched";
    private static final Set<String> ALLOWED_TEMPLATES = Set.of(
            HEALTH, READINESS, START, COLLECTION, HISTORY, ACTIVE, ENTRY, STOP, DASHBOARD_STATS, PREFERENCES, NOTES, NOTE
    );

    private RouteTemplates() {
    }

    public static String resolve(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String route && ALLOWED_TEMPLATES.contains(route)) {
            return route;
        }

        String path = request.getRequestURI();
        if (path != null) {
            int queryStart = path.indexOf('?');
            int fragmentStart = path.indexOf('#');
            int suffixStart = queryStart < 0
                    ? fragmentStart
                    : fragmentStart < 0 ? queryStart : Math.min(queryStart, fragmentStart);
            if (suffixStart >= 0) {
                path = path.substring(0, suffixStart);
            }
        }
        if (Set.of(HEALTH, READINESS, START, COLLECTION, HISTORY, ACTIVE, DASHBOARD_STATS, PREFERENCES, NOTES).contains(path)) {
            return path;
        }
        if (path != null && path.matches("/api/v1/notes/[0-9a-fA-F-]{36}")) {
            return NOTE;
        }
        if (path != null && path.matches("/api/v1/time-entries/[0-9a-fA-F-]{36}/stop")) {
            return STOP;
        }
        if (path != null && path.matches("/api/v1/time-entries/[0-9a-fA-F-]{36}")) {
            return ENTRY;
        }
        return UNMATCHED;
    }

    public static Optional<MutationRoute> mutation(HttpServletRequest request) {
        String route = resolve(request);
        if ("POST".equals(request.getMethod()) && (START.equals(route) || COLLECTION.equals(route) || NOTES.equals(route))) {
            return Optional.of(new MutationRoute("POST", route));
        }
        if ("PUT".equals(request.getMethod()) && (STOP.equals(route) || ENTRY.equals(route) || NOTE.equals(route))) {
            return Optional.of(new MutationRoute("PUT", route));
        }
        if ("DELETE".equals(request.getMethod()) && (ENTRY.equals(route) || NOTE.equals(route))) {
            return Optional.of(new MutationRoute("DELETE", route));
        }
        if ("PUT".equals(request.getMethod()) && PREFERENCES.equals(route)) {
            return Optional.of(new MutationRoute("PUT", PREFERENCES));
        }
        return Optional.empty();
    }

    public static boolean isNoteMutation(HttpServletRequest request) {
        String route = resolve(request);
        return (NOTES.equals(route) && "POST".equals(request.getMethod()))
                || (NOTE.equals(route) && ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod())));
    }

    public record MutationRoute(String method, String template) {
    }
}
