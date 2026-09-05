package com.wyx.travelagent.tools.model;

import java.util.Objects;

public record TravelSegment(TravelMode mode, Long distanceMeters, Long durationMinutes, boolean verified) {
    public TravelSegment {
        Objects.requireNonNull(mode, "mode");
        if (verified) {
            requireNonNegative(distanceMeters, "distanceMeters");
            requireNonNegative(durationMinutes, "durationMinutes");
        } else if (distanceMeters != null || durationMinutes != null) {
            throw new IllegalArgumentException("unverified segment cannot contain distance or duration");
        }
    }

    private static void requireNonNegative(Long value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
