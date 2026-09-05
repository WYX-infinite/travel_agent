package com.wyx.travelagent.tools.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolResultTest {

    private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsSuccessfulResultWithRequiredMetadataAndData() {
        ToolResult<String> result = ToolResult.success("amap", RETRIEVED_AT, "payload");

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo("amap");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data()).isEqualTo("payload");
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidSuccessfulResults")
    void rejectsSuccessfulResultWithContradictoryOrMissingFields(ToolResultFactory factory) {
        assertThatThrownBy(factory::create)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    static Stream<Arguments> invalidSuccessfulResults() {
        return Stream.of(
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, null, RETRIEVED_AT, "payload", null, null)),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, "  ", RETRIEVED_AT, "payload", null, null)),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, "amap", null, "payload", null, null)),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, "amap", RETRIEVED_AT, null, null, null)),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, "amap", RETRIEVED_AT, "payload", "ERROR", null)),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        true, "amap", RETRIEVED_AT, "payload", null, "failed"))
        );
    }

    @Test
    void createsFailedResultWithStableErrorCodeAndNoData() {
        ToolResult<String> result = ToolResult.failure(
                "open-meteo", RETRIEVED_AT, "PROVIDER_UNAVAILABLE", "request failed");

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo("open-meteo");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data()).isNull();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.errorMessage()).isEqualTo("request failed");
    }

    @ParameterizedTest
    @MethodSource("invalidFailedResults")
    void rejectsFailedResultWithDataOrWithoutStableErrorCode(ToolResultFactory factory) {
        assertThatThrownBy(factory::create)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    static Stream<Arguments> invalidFailedResults() {
        return Stream.of(
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        false, "amap", RETRIEVED_AT, "payload", "ERROR", "failed")),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        false, "amap", RETRIEVED_AT, null, null, "failed")),
                Arguments.of((ToolResultFactory) () -> new ToolResult<>(
                        false, "amap", RETRIEVED_AT, null, "  ", "failed"))
        );
    }

    @Test
    void serializesSuccessAndFailureWithStableFieldNames() throws Exception {
        JsonNode success = objectMapper.valueToTree(
                ToolResult.success("amap", RETRIEVED_AT, List.of("place")));
        JsonNode failure = objectMapper.valueToTree(
                ToolResult.failure("amap", RETRIEVED_AT, "RATE_LIMITED", "try later"));

        assertThat(success.fieldNames()).toIterable().containsExactly(
                "success", "provider", "retrievedAt", "data", "errorCode", "errorMessage");
        assertThat(failure.fieldNames()).toIterable().containsExactly(
                "success", "provider", "retrievedAt", "data", "errorCode", "errorMessage");
        assertThat(success.path("success").asBoolean()).isTrue();
        assertThat(success.path("data").get(0).asText()).isEqualTo("place");
        assertThat(failure.path("success").asBoolean()).isFalse();
        assertThat(failure.path("data").isNull()).isTrue();
        assertThat(failure.path("errorCode").asText()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void defensivelyCopiesListPayloads() {
        List<String> payload = new ArrayList<>(List.of("place"));
        ToolResult<List<String>> result = ToolResult.success("amap", RETRIEVED_AT, payload);

        payload.add("late mutation");

        assertThat(result.data()).containsExactly("place");
        assertThatThrownBy(() -> result.data().add("direct mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void roundTripsGenericPlaceListWithJackson() throws Exception {
        PlaceCandidate place = new PlaceCandidate(
                "B0FFG12345", "西湖", "杭州市西湖区",
                new GeoPoint(120.1551, 30.2741, CoordinateSystem.GCJ02));
        ToolResult<List<PlaceCandidate>> original = ToolResult.success(
                "amap", RETRIEVED_AT, List.of(place));

        String json = objectMapper.writeValueAsString(original);
        var resultType = objectMapper.getTypeFactory().constructParametricType(
                ToolResult.class,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PlaceCandidate.class));
        ToolResult<List<PlaceCandidate>> restored = objectMapper.readValue(json, resultType);

        assertThat(restored).isEqualTo(original);
        assertThatThrownBy(() -> restored.data().add(place))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("boundaryCoordinates")
    void acceptsInclusiveCoordinateBoundaries(double longitude, double latitude) {
        GeoPoint point = new GeoPoint(longitude, latitude, CoordinateSystem.WGS84);

        assertThat(point.longitude()).isEqualTo(longitude);
        assertThat(point.latitude()).isEqualTo(latitude);
    }

    static Stream<Arguments> boundaryCoordinates() {
        return Stream.of(
                Arguments.of(-180.0, 0.0),
                Arguments.of(180.0, 0.0),
                Arguments.of(0.0, -90.0),
                Arguments.of(0.0, 90.0)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void rejectsOutOfRangeOrNonFiniteCoordinates(double longitude, double latitude) {
        assertThatThrownBy(() -> new GeoPoint(longitude, latitude, CoordinateSystem.WGS84))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.of(
                Arguments.of(-180.000001, 0.0),
                Arguments.of(180.000001, 0.0),
                Arguments.of(0.0, -90.000001),
                Arguments.of(0.0, 90.000001),
                Arguments.of(Double.NaN, 0.0),
                Arguments.of(Double.POSITIVE_INFINITY, 0.0),
                Arguments.of(Double.NEGATIVE_INFINITY, 0.0),
                Arguments.of(0.0, Double.NaN),
                Arguments.of(0.0, Double.POSITIVE_INFINITY),
                Arguments.of(0.0, Double.NEGATIVE_INFINITY)
        );
    }

    @Test
    void rejectsMissingCoordinateSystem() {
        assertThatThrownBy(() -> new GeoPoint(120.1551, 30.2741, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("coordinateSystem");
    }

    @Test
    void supportsBothExplicitCoordinateSystems() {
        GeoPoint wgs84 = new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84);
        GeoPoint gcj02 = new GeoPoint(120.1551, 30.2741, CoordinateSystem.GCJ02);

        assertThat(wgs84.coordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
        assertThat(gcj02.coordinateSystem()).isEqualTo(CoordinateSystem.GCJ02);
    }

    @Test
    void placeCandidateStoresItsPositionAsGeoPoint() {
        GeoPoint location = new GeoPoint(120.1551, 30.2741, CoordinateSystem.GCJ02);
        PlaceCandidate candidate = new PlaceCandidate("B0FFG12345", "西湖", "杭州市西湖区", location);

        assertThat(candidate.location()).isSameAs(location);
        assertThat(PlaceCandidate.class.getRecordComponents())
                .filteredOn(component -> component.getName().equals("location"))
                .singleElement()
                .extracting(component -> component.getType())
                .isEqualTo(GeoPoint.class);
        assertThat(PlaceCandidate.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(double.class));
    }

    @Test
    void geocodedAddressMayOmitPoiIdWithoutUsingBlankSentinel() {
        GeoPoint location = new GeoPoint(120.1551, 30.2741, CoordinateSystem.GCJ02);

        PlaceCandidate geocodedAddress = new PlaceCandidate(
                null, "湖滨住宿位置", "杭州市上城区湖滨街道", location);

        assertThat(geocodedAddress.poiId()).isNull();
        assertThatThrownBy(() -> new PlaceCandidate(
                "  ", "湖滨住宿位置", "杭州市上城区湖滨街道", location))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weatherForecastDefensivelyCopiesDailyEntries() {
        WeatherResult.DailyForecast forecast = new WeatherResult.DailyForecast(
                LocalDate.of(2026, 9, 9), 20.0, 28.0, 60, 4.5);
        List<WeatherResult.DailyForecast> entries = new ArrayList<>(List.of(forecast));

        WeatherResult result = new WeatherResult(entries);
        entries.clear();

        assertThat(result.dailyForecasts()).containsExactly(forecast);
        assertThatThrownBy(() -> result.dailyForecasts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void travelSegmentUsesExplicitUnitsAndUnverifiedNullSemantics() {
        TravelSegment verified = new TravelSegment(TravelMode.TRANSIT, 5_200L, 35L, true);
        TravelSegment unverified = new TravelSegment(TravelMode.TRANSIT, null, null, false);

        assertThat(verified.distanceMeters()).isEqualTo(5_200L);
        assertThat(verified.durationMinutes()).isEqualTo(35L);
        assertThat(unverified.distanceMeters()).isNull();
        assertThat(unverified.durationMinutes()).isNull();
        assertThatThrownBy(() -> new TravelSegment(TravelMode.TRANSIT, 5_200L, 35L, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @FunctionalInterface
    interface ToolResultFactory {
        ToolResult<?> create();
    }
}
