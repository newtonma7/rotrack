package com.rotrack.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import org.postgresql.Driver;
import org.postgresql.PGProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup when the effective PostgreSQL driver mode weakens the transport boundary.
 * The JDBC URL is authoritative because PostgreSQL URL parameters override other driver properties.
 */
@Component
public class DatabaseTlsValidator {

    static final String MANAGED_MODE = "verify-full";
    static final String LOCAL_MODE = "disable";

    public DatabaseTlsValidator(
            @Value("${spring.datasource.url}") String databaseUrl,
            Environment environment
    ) {
        validate(databaseUrl, Arrays.asList(environment.getActiveProfiles()).contains("local"));
    }

    static void validate(String databaseUrl, boolean localProfile) {
        Properties effective = Driver.parseURL(databaseUrl, new Properties());
        if (effective == null) {
            throw new IllegalStateException("DATABASE_URL must be a valid PostgreSQL JDBC URL");
        }

        String mode = effective.getProperty("sslmode", "").toLowerCase(Locale.ROOT);
        if (MANAGED_MODE.equals(mode)) {
            rejectCustomTlsImplementations(effective);
            String rootCertificate = effective.getProperty("sslrootcert", "").trim();
            if (rootCertificate.isEmpty()) {
                throw new IllegalStateException(
                        "DATABASE_URL sslmode=verify-full requires an explicit sslrootcert CA path");
            }
            return;
        }
        if (localProfile && LOCAL_MODE.equals(mode) && hasOnlyLoopbackHosts(effective)) {
            return;
        }
        throw new IllegalStateException(localProfile
                ? "The local profile permits sslmode=disable only for loopback PostgreSQL; otherwise use verify-full"
                : "Managed PostgreSQL requires DATABASE_URL sslmode=verify-full");
    }

    private static void rejectCustomTlsImplementations(Properties effective) {
        for (String property : new String[]{"sslfactory", "sslfactoryarg", "sslhostnameverifier"}) {
            if (!effective.getProperty(property, "").isBlank()) {
                throw new IllegalStateException(
                        "DATABASE_URL must not override PostgreSQL TLS verification implementations");
            }
        }
    }

    private static boolean hasOnlyLoopbackHosts(Properties effective) {
        String hosts = PGProperty.PG_HOST.getOrDefault(effective);
        return hosts != null && !hosts.isBlank() && Arrays.stream(hosts.split(","))
                .map(String::trim)
                .map(host -> host.startsWith("[") && host.endsWith("]")
                        ? host.substring(1, host.length() - 1)
                        : host)
                .allMatch(host -> host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
    }
}
