package com.wyx.travelagent.tools.model;

import java.util.Objects;

public record GeoPoint(
        double longitude,
        double latitude,
        CoordinateSystem coordinateSystem
) {
    public GeoPoint {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range");
        }
        Objects.requireNonNull(coordinateSystem, "coordinateSystem");
    }
}
