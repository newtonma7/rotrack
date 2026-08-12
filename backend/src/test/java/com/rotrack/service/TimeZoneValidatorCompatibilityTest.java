package com.rotrack.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class TimeZoneValidatorCompatibilityTest {

    @Test
    void acceptsRepresentativeJavaAndPostgresIanaIdentifiers() {
        for (String timeZone : new String[]{
                "UTC", "America/New_York", "Europe/Berlin", "Asia/Tokyo", "Australia/Lord_Howe"
        }) {
            assertThatCode(() -> TimeZoneValidator.parse(timeZone)).doesNotThrowAnyException();
        }
    }
}
