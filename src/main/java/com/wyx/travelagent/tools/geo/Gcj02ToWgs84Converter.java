package com.wyx.travelagent.tools.geo;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;

import java.util.Objects;

public class Gcj02ToWgs84Converter {
    private static final double SEMI_MAJOR_AXIS = 6_378_245.0;
    private static final double ECCENTRICITY_SQUARED = 0.00669342162296594323;
    private static final double[][] MAINLAND_BOUNDARY = {
            {73.5, 39.0}, {75.0, 36.0}, {78.0, 33.0}, {81.0, 30.0},
            {88.0, 27.5}, {92.0, 28.0}, {97.0, 25.0}, {99.0, 23.0},
            {101.0, 21.0}, {103.0, 22.0}, {106.0, 21.5}, {108.0, 22.0},
            {111.0, 21.0}, {113.0, 22.0}, {115.0, 22.5}, {117.0, 23.0},
            {119.5, 25.0}, {120.5, 27.0}, {121.7, 30.0}, {121.8, 32.0},
            {121.0, 34.0}, {122.0, 37.0}, {124.0, 40.0}, {128.0, 42.0},
            {131.0, 43.0}, {131.0, 45.0}, {134.8, 47.5}, {134.7, 48.5},
            {128.0, 49.5}, {123.0, 53.6}, {120.0, 53.0}, {108.0, 53.5},
            {100.0, 51.0}, {96.0, 49.0}, {88.0, 49.0},
            {82.0, 46.0}, {79.0, 43.0}
    };

    public GeoPoint convert(GeoPoint source) {
        Objects.requireNonNull(source, "source");
        if (source.coordinateSystem() == CoordinateSystem.WGS84) {
            return source;
        }
        if (outsideMainlandChina(source.longitude(), source.latitude())) {
            return new GeoPoint(source.longitude(), source.latitude(), CoordinateSystem.WGS84);
        }

        double latitudeOffset = transformLatitude(source.longitude() - 105.0, source.latitude() - 35.0);
        double longitudeOffset = transformLongitude(source.longitude() - 105.0, source.latitude() - 35.0);
        double radians = Math.toRadians(source.latitude());
        double sinLatitude = Math.sin(radians);
        double magic = 1.0 - ECCENTRICITY_SQUARED * sinLatitude * sinLatitude;
        double squareRootMagic = Math.sqrt(magic);
        latitudeOffset = latitudeOffset * 180.0
                / ((SEMI_MAJOR_AXIS * (1.0 - ECCENTRICITY_SQUARED)) / (magic * squareRootMagic) * Math.PI);
        longitudeOffset = longitudeOffset * 180.0
                / (SEMI_MAJOR_AXIS / squareRootMagic * Math.cos(radians) * Math.PI);

        double projectedLatitude = source.latitude() + latitudeOffset;
        double projectedLongitude = source.longitude() + longitudeOffset;
        return new GeoPoint(
                source.longitude() * 2.0 - projectedLongitude,
                source.latitude() * 2.0 - projectedLatitude,
                CoordinateSystem.WGS84);
    }

    private static boolean outsideMainlandChina(double longitude, double latitude) {
        // A conservative polygon avoids transforming nearby countries and Taiwan as GCJ-02.
        return !insidePolygon(longitude, latitude, MAINLAND_BOUNDARY)
                && !insideRectangle(longitude, latitude, 108.5, 111.5, 18.0, 20.5);
    }

    private static boolean insidePolygon(double longitude, double latitude, double[][] boundary) {
        boolean inside = false;
        for (int current = 0, previous = boundary.length - 1;
             current < boundary.length;
             previous = current++) {
            double currentLongitude = boundary[current][0];
            double currentLatitude = boundary[current][1];
            double previousLongitude = boundary[previous][0];
            double previousLatitude = boundary[previous][1];
            boolean crossesLatitude = (currentLatitude > latitude) != (previousLatitude > latitude);
            if (crossesLatitude) {
                double crossingLongitude = (previousLongitude - currentLongitude)
                        * (latitude - currentLatitude)
                        / (previousLatitude - currentLatitude)
                        + currentLongitude;
                if (longitude < crossingLongitude) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static boolean insideRectangle(
            double longitude,
            double latitude,
            double minimumLongitude,
            double maximumLongitude,
            double minimumLatitude,
            double maximumLatitude
    ) {
        return longitude >= minimumLongitude && longitude <= maximumLongitude
                && latitude >= minimumLatitude && latitude <= maximumLatitude;
    }

    private static double transformLatitude(double x, double y) {
        double value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(y * Math.PI)
                + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        value += (160.0 * Math.sin(y / 12.0 * Math.PI)
                + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return value;
    }

    private static double transformLongitude(double x, double y) {
        double value = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(x * Math.PI)
                + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        value += (150.0 * Math.sin(x / 12.0 * Math.PI)
                + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return value;
    }
}
