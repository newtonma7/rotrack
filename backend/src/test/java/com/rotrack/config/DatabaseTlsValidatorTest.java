package com.rotrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;

class DatabaseTlsValidatorTest {

    @Test
    void managedDatabaseRequiresEffectiveVerifyFullMode() {
        assertThatCode(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://db.example.test:5432/postgres?sslmode=verify-full"
                        + "&sslrootcert=/run/secrets/supabase-db-ca.crt",
                false
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://db.example.test:5432/postgres?sslmode=verify-full",
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("DATABASE_URL sslmode=verify-full requires an explicit sslrootcert CA path");

        assertThatThrownBy(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://db.example.test:5432/postgres?sslmode=require",
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Managed PostgreSQL requires DATABASE_URL sslmode=verify-full");
    }

    @Test
    void localProfileAloneMayDisableTlsForLoopbackPostgres() {
        assertThatCode(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://localhost:5432/postgres?sslmode=disable",
                true
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://localhost:5432/postgres?sslmode=disable",
                false
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://db.example.test:5432/postgres?sslmode=disable",
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("The local profile permits sslmode=disable only for loopback PostgreSQL; otherwise use verify-full");
        assertThatThrownBy(() -> DatabaseTlsValidator.validate(
                "jdbc:postgresql://localhost,db.example.test:5432/postgres?sslmode=disable",
                true
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jdbcUrlOverridesOtherDriverPropertiesSoOnlyTheUrlDefinesTlsMode() {
        Properties fallback = new Properties();
        fallback.setProperty("sslmode", "verify-full");

        Properties effective = Driver.parseURL(
                "jdbc:postgresql://db.example.test:5432/postgres?sslmode=require",
                fallback
        );

        assertThat(effective).isNotNull();
        assertThat(effective.getProperty("sslmode")).isEqualTo("require");
    }
}
