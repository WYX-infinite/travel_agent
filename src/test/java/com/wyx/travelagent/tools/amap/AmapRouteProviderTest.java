package com.wyx.travelagent.tools.amap;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapRouteProviderTest {

    private static final String BASE_URL = "https://amap.test";
    private static final String API_KEY = "test-api-key";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 9, 10, 12, 0);
    private static final PlaceCandidate FROM = new PlaceCandidate(
            "FROM-POI", "起点", "杭州市上城区",
            new GeoPoint(120.155070, 30.274084, CoordinateSystem.GCJ02));
    private static final PlaceCandidate TO = new PlaceCandidate(
            "TO-POI", "终点", "杭州市西湖区",
            new GeoPoint(120.164520, 30.254460, CoordinateSystem.GCJ02));

    @ParameterizedTest
    @MethodSource("supportedModes")
    void mapsSupportedModesToOfficialEndpointsAndVerifiedSegments(
            TravelMode requestedMode,
            String endpoint,
            String response,
            boolean expectsCity,
            PoiIdStyle poiIdStyle) {
        Harness harness = harness();
        var expectation = harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + endpoint)))
                .andExpect(queryParam("key", API_KEY))
                .andExpect(queryParam("origin", "120.155070,30.274084"))
                .andExpect(queryParam("destination", "120.164520,30.254460"))
                .andExpect(queryParam("output", "JSON"));
        if (expectsCity) {
            expectation.andExpect(queryParam("city", "%E6%9D%AD%E5%B7%9E"));
        }
        switch (poiIdStyle) {
            case WALKING -> expectation
                    .andExpect(queryParam("origin_id", "FROM-POI"))
                    .andExpect(queryParam("destination_id", "TO-POI"))
                    .andExpect(withoutQueryParams("originid", "destinationid"));
            case DRIVING -> expectation
                    .andExpect(queryParam("originid", "FROM-POI"))
                    .andExpect(queryParam("destinationid", "TO-POI"))
                    .andExpect(withoutQueryParams("origin_id", "destination_id"));
            case NONE -> expectation
                    .andExpect(withoutQueryParams(
                            "origin_id", "destination_id", "originid", "destinationid"));
        }
        expectation.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, requestedMode);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo("amap");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data()).isEqualTo(new TravelSegment(requestedMode, 1_500L, 2L, true));
        harness.server.verify();
    }

    static Stream<Arguments> supportedModes() {
        String ordinary = "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"1500\",\"duration\":\"61\"}]}}";
        String transit = "{\"status\":\"1\",\"route\":{\"transits\":[{\"distance\":\"1500\",\"duration\":\"61\"}]}}";
        return Stream.of(
                Arguments.of(TravelMode.WALKING, "/v3/direction/walking", ordinary, false, PoiIdStyle.WALKING),
                Arguments.of(TravelMode.DRIVING, "/v3/direction/driving", ordinary, false, PoiIdStyle.DRIVING),
                Arguments.of(TravelMode.TRANSIT, "/v3/direction/transit/integrated", transit, true, PoiIdStyle.NONE),
                Arguments.of(TravelMode.TAXI, "/v3/direction/driving", ordinary, false, PoiIdStyle.DRIVING)
        );
    }

    @Test
    void mapsAmapHttp200BusinessRateLimitToStableError() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/driving")))
                .andRespond(withSuccess(
                        "{\"status\":\"0\",\"info\":\"ACCESS_TOO_FREQUENT\",\"infocode\":\"10004\"}",
                        MediaType.APPLICATION_JSON));

        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, TravelMode.DRIVING);

        assertFailure(result, "PROVIDER_RATE_LIMITED");
        harness.server.verify();
    }

    @Test
    void roundsAnyPositiveDurationUpToAtLeastOneMinute() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/walking")))
                .andRespond(withSuccess(
                        "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"8\",\"duration\":\"1\"}]}}",
                        MediaType.APPLICATION_JSON));

        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, TravelMode.WALKING);

        assertThat(result.data().durationMinutes()).isEqualTo(1L);
        harness.server.verify();
    }

    @ParameterizedTest
    @MethodSource("invalidRouteResponses")
    void rejectsMissingNegativeNonNumericAndEmptyRouteFacts(String response) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/driving")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, TravelMode.DRIVING);

        assertFailure(result, "INVALID_PROVIDER_RESPONSE");
        harness.server.verify();
    }

    static Stream<String> invalidRouteResponses() {
        return Stream.of(
                "{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"60\"}]}}",
                "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"-1\",\"duration\":\"60\"}]}}",
                "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"100\",\"duration\":\"NaN\"}]}}",
                "{\"status\":\"0\",\"info\":\"missing infocode\"}",
                "not-json"
        );
    }

    @Test
    void returnsRouteNotFoundForValidEmptyProviderResult() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/walking")))
                .andRespond(withSuccess("{\"status\":\"1\",\"route\":{\"paths\":[]}}",
                        MediaType.APPLICATION_JSON));

        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, TravelMode.WALKING);

        assertFailure(result, "ROUTE_NOT_FOUND");
        harness.server.verify();
    }

    @Test
    void rejectsWgs84CoordinatesWithoutSendingHttpRequest() {
        AmapRouteProvider provider = noNetworkProvider();
        PlaceCandidate wgs84 = new PlaceCandidate(
                "WGS", "WGS84地点", "杭州",
                new GeoPoint(120.15, 30.27, CoordinateSystem.WGS84));

        assertFailure(provider.route("杭州", wgs84, TO, TravelMode.WALKING),
                "COORDINATE_SYSTEM_UNSUPPORTED");
        assertFailure(provider.route("杭州", FROM, wgs84, TravelMode.DRIVING),
                "COORDINATE_SYSTEM_UNSUPPORTED");
    }

    @Test
    void rejectsNullOrIncompleteRequestsWithoutSendingHttpRequest() {
        AmapRouteProvider provider = noNetworkProvider();

        assertFailure(provider.route("杭州", null, TO, TravelMode.WALKING), "INVALID_COORDINATE");
        assertFailure(provider.route("杭州", FROM, null, TravelMode.WALKING), "INVALID_COORDINATE");
        assertFailure(provider.route("杭州", FROM, TO, null), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.route(" ", FROM, TO, TravelMode.TRANSIT), "INVALID_PROVIDER_REQUEST");
    }

    @Test
    void mapsRateLimitServerFailureAndTimeoutToStableErrors() {
        assertTransportFailure(withStatus(HttpStatus.TOO_MANY_REQUESTS), "PROVIDER_RATE_LIMITED");
        assertTransportFailure(withServerError(), "PROVIDER_UNAVAILABLE");

        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/driving")))
                .andRespond(request -> { throw new ResourceAccessException("simulated timeout"); });
        ToolResult<TravelSegment> timeout = harness.provider.route("杭州", FROM, TO, TravelMode.DRIVING);
        assertFailure(timeout, "PROVIDER_TIMEOUT");
        assertThat(timeout.errorMessage()).doesNotContain(API_KEY).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    @Test
    void doesNotModifyInputCandidatesOrCoordinates() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/walking")))
                .andRespond(withSuccess(
                        "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"100\",\"duration\":\"60\"}]}}",
                        MediaType.APPLICATION_JSON));
        PlaceCandidate fromBefore = new PlaceCandidate(FROM.poiId(), FROM.name(), FROM.address(), FROM.location());
        PlaceCandidate toBefore = new PlaceCandidate(TO.poiId(), TO.name(), TO.address(), TO.location());

        harness.provider.route("杭州", FROM, TO, TravelMode.WALKING);

        assertThat(FROM).isEqualTo(fromBefore);
        assertThat(TO).isEqualTo(toBefore);
        harness.server.verify();
    }

    private void assertTransportFailure(org.springframework.test.web.client.ResponseCreator response,
                                        String expectedCode) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/direction/driving")))
                .andRespond(response);
        ToolResult<TravelSegment> result = harness.provider.route("杭州", FROM, TO, TravelMode.DRIVING);
        assertFailure(result, expectedCode);
        assertThat(result.errorMessage()).doesNotContain(API_KEY).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    private static void assertFailure(ToolResult<?> result, String expectedCode) {
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(expectedCode);
        assertThat(result.data()).isNull();
    }

    private static Harness harness() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(server, new AmapRouteProvider(builder.build(), properties(), CLOCK));
    }

    private static AmapRouteProvider noNetworkProvider() {
        RestClient client = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, method) -> { throw new AssertionError("HTTP request was not expected"); })
                .build();
        return new AmapRouteProvider(client, properties(), CLOCK);
    }

    private static AmapProperties properties() {
        return new AmapProperties(BASE_URL, API_KEY, Duration.ofMillis(500), Duration.ofSeconds(2));
    }

    private static RequestMatcher withoutQueryParams(String... names) {
        return request -> {
            var queryParameters = UriComponentsBuilder.fromUri(request.getURI())
                    .build(true)
                    .getQueryParams();
            assertThat(queryParameters).doesNotContainKeys(names);
        };
    }

    private enum PoiIdStyle { WALKING, DRIVING, NONE }

    private record Harness(MockRestServiceServer server, AmapRouteProvider provider) {}
}
