package com.rotrack.validation;

import com.rotrack.service.TimeZoneValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class TimeZoneConstraintValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            TimeZoneValidator.parse(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
