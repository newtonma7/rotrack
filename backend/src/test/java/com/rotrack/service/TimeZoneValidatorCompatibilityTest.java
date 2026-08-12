package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class TimeZoneValidatorCompatibilityTest {

    @Test
    void acceptsOnlyUtcOrSlashFormJavaAndPostgresIanaIdentifiers() {
        for (String timeZone : new String[]{
                "UTC", "America/New_York", "Europe/Berlin", "Asia/Tokyo", "Australia/Lord_Howe"
        }) {
            assertThatCode(() -> TimeZoneValidator.parse(timeZone)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsJavaAliasesThatMigration004Rejects() {
        for (String timeZone : new String[]{"GMT", "UCT", "Zulu"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> TimeZoneValidator.parse(timeZone))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeZone must be a valid IANA identifier");
        }
    }
}
