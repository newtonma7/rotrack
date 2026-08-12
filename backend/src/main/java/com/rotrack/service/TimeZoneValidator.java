package com.rotrack.service;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

final class TimeZoneValidator {

    private TimeZoneValidator() {
    }

    static ZoneId parse(String timeZone) {
        try {
            if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone)) {
                throw new IllegalArgumentException("timeZone must be a valid IANA identifier");
            }
            return ZoneId.of(timeZone);
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("timeZone must be a valid IANA identifier", exception);
        }
    }
}
