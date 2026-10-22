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
    public static final String DAILY_LOGS = "/api/v1/daily-logs";
    public static final String DAILY_LOG = "/api/v1/daily-logs/{localDate}";
    public static final String DAILY_LOG_REFLECTION = "/api/v1/daily-logs/{localDate}/reflection";
    public static final String USER_SEARCH = "/api/v1/users/search";
    public static final String FRIENDS = "/api/v1/friends";
    public static final String FRIEND = "/api/v1/friends/{id}";
    public static final String REQUESTS = "/api/v1/friend-requests";
    public static final String REQUEST = "/api/v1/friend-requests/{id}";
    public static final String ACCEPT = "/api/v1/friend-requests/{id}/accept";
    public static final String BLOCKS = "/api/v1/blocks";
    public static final String BLOCK = "/api/v1/blocks/{username}";
    public static final String STUDY_SUMMARY = "/api/v1/social/summaries/{username}";
    public static final String STUDY_PRESENCE = "/api/v1/social/presence/{username}";
    private static final String UNMATCHED = "/unmatched";
    private static final Set<String> ALLOWED_TEMPLATES = Set.of(
            HEALTH, READINESS, START, COLLECTION, HISTORY, ACTIVE, ENTRY, STOP, DASHBOARD_STATS, PREFERENCES, NOTES, NOTE, DAILY_LOGS, DAILY_LOG, DAILY_LOG_REFLECTION,
            USER_SEARCH, FRIENDS, FRIEND, REQUESTS, REQUEST, ACCEPT, BLOCKS, BLOCK, STUDY_SUMMARY, STUDY_PRESENCE
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
        if (Set.of(HEALTH, READINESS, START, COLLECTION, HISTORY, ACTIVE, DASHBOARD_STATS, PREFERENCES, NOTES, DAILY_LOGS, USER_SEARCH, FRIENDS, REQUESTS, BLOCKS).contains(path)) {
            return path;
        }
        if (path != null && path.matches("/api/v1/friend-requests/[^/]+/accept")) return ACCEPT;
        if (path != null && path.matches("/api/v1/friend-requests/[^/]+")) return REQUEST;
        if (path != null && path.matches("/api/v1/friends/[^/]+")) return FRIEND;
        if (path != null && path.matches("/api/v1/blocks/[^/]+")) return BLOCK;
        if (path != null && path.matches("/api/v1/social/summaries/[^/]+")) return STUDY_SUMMARY;
        if (path != null && path.matches("/api/v1/social/presence/[^/]+")) return STUDY_PRESENCE;
        // Match the route shape before validating dates so malformed labels cannot bypass
        // authenticated save budgets or the pre-deserialization body cap.
        if (path != null && path.matches("/api/v1/daily-logs/[^/]+/reflection")) return DAILY_LOG_REFLECTION;
        if (path != null && path.matches("/api/v1/daily-logs/[^/]+")) return DAILY_LOG;
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
        if ("POST".equals(request.getMethod()) && (REQUESTS.equals(route) || ACCEPT.equals(route)))
            return Optional.of(new MutationRoute("POST", route));
        if (BLOCK.equals(route) && ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod())))
            return Optional.of(new MutationRoute(request.getMethod(), route));
        if ("DELETE".equals(request.getMethod()) && (FRIEND.equals(route) || REQUEST.equals(route)))
            return Optional.of(new MutationRoute("DELETE", route));
        if ("POST".equals(request.getMethod()) && (START.equals(route) || COLLECTION.equals(route) || NOTES.equals(route))) {
            return Optional.of(new MutationRoute("POST", route));
        }
        if ("PUT".equals(request.getMethod()) && (STOP.equals(route) || ENTRY.equals(route) || NOTE.equals(route))) {
            return Optional.of(new MutationRoute("PUT", route));
        }
        if ("DELETE".equals(request.getMethod()) && (ENTRY.equals(route) || NOTE.equals(route))) {
            return Optional.of(new MutationRoute("DELETE", route));
        }
        if ("PUT".equals(request.getMethod()) && DAILY_LOG_REFLECTION.equals(route)) return Optional.of(new MutationRoute("PUT", route));
        if ("PUT".equals(request.getMethod()) && PREFERENCES.equals(route)) {
            return Optional.of(new MutationRoute("PUT", PREFERENCES));
        }
        return Optional.empty();
    }

    public static boolean isNoteMutation(HttpServletRequest request) {
        String route = resolve(request);
        return (NOTES.equals(route) && "POST".equals(request.getMethod()))
                || (NOTE.equals(route) && ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod())))
                || (DAILY_LOG_REFLECTION.equals(route) && "PUT".equals(request.getMethod()));
    }

    public record MutationRoute(String method, String template) {
    }
}
