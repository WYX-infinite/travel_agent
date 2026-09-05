package com.wyx.travelagent.tools.geo;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class Gcj02ToWgs84ConverterTest {

    private static final double FIXTURE_TOLERANCE_DEGREES = 0.00001;
    private final Gcj02ToWgs84Converter converter = new Gcj02ToWgs84Converter();

    @Test
    void convertsPublishedBeijingGcj02FixtureToWgs84Locally() {
        GeoPoint source = new GeoPoint(116.404000, 39.915000, CoordinateSystem.GCJ02);

        GeoPoint converted = converter.convert(source);

        assertThat(converted.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
        assertThat(converted.longitude()).isCloseTo(116.397756,
                org.assertj.core.data.Offset.offset(FIXTURE_TOLERANCE_DEGREES));
        assertThat(converted.latitude()).isCloseTo(39.913596,
                org.assertj.core.data.Offset.offset(FIXTURE_TOLERANCE_DEGREES));
        assertThat(source).isEqualTo(new GeoPoint(116.404000, 39.915000, CoordinateSystem.GCJ02));
    }

    @Test
    void leavesExistingWgs84PointNumericallyAndSemanticallyUnchanged() {
        GeoPoint source = new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84);

        GeoPoint converted = converter.convert(source);

        assertThat(converted).isSameAs(source);
        assertThat(converted.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
    }

    @ParameterizedTest
    @CsvSource({
            "2.3522, 48.8566",
            "-0.1276, 51.5072",
            "151.2093, -33.8688",
            "126.9780, 37.5665",
            "121.5654, 25.0330",
            "105.8342, 21.0278"
    })
    void doesNotApplyGcjOffsetOutsideMainlandChina(double longitude, double latitude) {
        GeoPoint source = new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02);

        GeoPoint converted = converter.convert(source);

        assertThat(converted.longitude()).isEqualTo(longitude);
        assertThat(converted.latitude()).isEqualTo(latitude);
        assertThat(converted.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
        assertThat(source.coordinateSystem()).isEqualTo(CoordinateSystem.GCJ02);
    }

    @ParameterizedTest
    @CsvSource({
            "122.5363, 52.9721",
            "134.3079, 48.3647"
    })
    void appliesGcjOffsetAtMainlandNortheasternBorderCities(double longitude, double latitude) {
        GeoPoint converted = converter.convert(
                new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02));

        assertThat(converted.longitude()).isNotEqualTo(longitude);
        assertThat(converted.latitude()).isNotEqualTo(latitude);
        assertThat(converted.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
    }

    @ParameterizedTest
    @CsvSource({
            "72.004, 0.8293",
            "137.8347, 55.8271",
            "120.1551, 30.2741"
    })
    void conversionAlwaysProducesFiniteInRangeWgs84Coordinates(double longitude, double latitude) {
        GeoPoint converted = converter.convert(
                new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02));

        assertThat(converted.longitude()).isFinite().isBetween(-180.0, 180.0);
        assertThat(converted.latitude()).isFinite().isBetween(-90.0, 90.0);
        assertThat(converted.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
    }
}
