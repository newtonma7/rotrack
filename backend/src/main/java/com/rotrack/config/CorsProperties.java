package com.rotrack.config;

import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Exact browser origins allowed to send credentialed API requests. */
@Validated
@ConfigurationProperties(prefix = "rotrack.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
        allowedOrigins.forEach(CorsProperties::validateOrigin);
    }

    private static void validateOrigin(String value) {
        try {
            URI origin = new URI(value);
            String scheme = origin.getScheme() == null ? "" : origin.getScheme().toLowerCase(Locale.ROOT);
            String host = origin.getHost();
            boolean noResourceParts = (origin.getPath() == null || origin.getPath().isEmpty())
                    && origin.getQuery() == null
                    && origin.getFragment() == null
                    && origin.getUserInfo() == null;
            boolean validPort = origin.getPort() >= -1 && origin.getPort() <= 65_535;
            boolean secure = "https".equals(scheme);
            boolean localHttp = "http".equals(scheme) && isLoopbackHost(host);
            if (host == null || value.contains("*") || !noResourceParts || !validPort || (!secure && !localHttp)) {
                throw invalidOrigin();
            }
        } catch (URISyntaxException exception) {
            throw invalidOrigin();
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]"));
    }

    private static IllegalArgumentException invalidOrigin() {
        return new IllegalArgumentException(
                "CORS origins must be exact HTTPS origins, or HTTP loopback origins for local development"
        );
    }
}
