package com.rotrack.security;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for the single authenticated time-entry mutation budget. */
public record MutationRateKey(UUID subject) {

    public MutationRateKey {
        Objects.requireNonNull(subject, "subject");
    }
}
