package com.rotrack.service;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

public final class TimeZoneValidator {

    private TimeZoneValidator() {
    }

    public static ZoneId parse(String timeZone) {
        // Keep Java's broader alias catalog aligned with migration 004: UTC or a known slash-form zone.
        try {
            if (timeZone == null
                    || (!"UTC".equals(timeZone)
                    && (!timeZone.contains("/") || !ZoneId.getAvailableZoneIds().contains(timeZone)))) {
                throw new IllegalArgumentException("timeZone must be a valid IANA identifier");
            }
            return ZoneId.of(timeZone);
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("timeZone must be a valid IANA identifier", exception);
        }
    }
}
