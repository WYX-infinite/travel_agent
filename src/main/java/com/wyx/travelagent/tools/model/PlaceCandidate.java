package com.wyx.travelagent.tools.model;

import java.util.Objects;

public record PlaceCandidate(String poiId, String name, String address, GeoPoint location) {
    public PlaceCandidate {
        // A geocoded address may not identify a POI; search results must still supply a real POI ID.
        if (poiId != null && poiId.isBlank()) {
            throw new IllegalArgumentException("poiId must not be blank");
        }
        requireNonBlank(name, "name");
        requireNonBlank(address, "address");
        Objects.requireNonNull(location, "location");
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
