package com.wyx.travelagent.tools.amap;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapPlaceProviderTest {

    private static final String BASE_URL = "https://amap.test";
    private static final String API_KEY = "test-api-key";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 9, 10, 12, 0);

    @Test
    void searchesPoisWithOfficialParametersAndMapsImmutableGcj02Candidates() {
        Harness harness = harness();
        harness.server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("key", API_KEY))
                .andExpect(queryParam("keywords", "%E8%A5%BF%E6%B9%96"))
                .andExpect(queryParam("city", "%E6%9D%AD%E5%B7%9E"))
                .andExpect(queryParam("citylimit", "true"))
                .andExpect(queryParam("offset", "2"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("extensions", "base"))
                .andExpect(queryParam("output", "JSON"))
                .andRespond(withSuccess("""
                        {"status":"1","info":"OK","count":"1","pois":[
                          {"id":"B0FFG12345","name":"西湖风景名胜区","address":"杭州市西湖区龙井路1号","location":"120.155070,30.274084"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        ToolResult<List<PlaceCandidate>> result = harness.provider.search("杭州", "西湖", 2);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo("amap");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data()).containsExactly(new PlaceCandidate(
                "B0FFG12345", "西湖风景名胜区", "杭州市西湖区龙井路1号",
                new com.wyx.travelagent.tools.model.GeoPoint(120.155070, 30.274084, CoordinateSystem.GCJ02)));
        assertThatThrownBy(() -> result.data().clear()).isInstanceOf(UnsupportedOperationException.class);
        harness.server.verify();
    }

    @Test
    void returnsSuccessfulEmptyListWhenAmapFindsNoPois() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andRespond(withSuccess("{" + "\"status\":\"1\",\"count\":\"0\",\"pois\":[]}",
                        MediaType.APPLICATION_JSON));

        ToolResult<List<PlaceCandidate>> result = harness.provider.search("杭州", "不存在的地点", 10);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
        harness.server.verify();
    }

    @Test
    void acceptsOfficialMaximumV3PageSizeOfTwentyFive() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andExpect(queryParam("offset", "25"))
                .andRespond(withSuccess("{\"status\":\"1\",\"count\":\"0\",\"pois\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(harness.provider.search("杭州", "西湖", 25).success()).isTrue();
        harness.server.verify();
    }

    @ParameterizedTest
    @MethodSource("invalidPoiResponses")
    void rejectsInvalidPoiEntriesWithoutLeakingVendorPayload(String response) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ToolResult<List<PlaceCandidate>> result = harness.provider.search("杭州", "西湖", 10);

        assertFailure(result, "INVALID_PROVIDER_RESPONSE");
        assertThat(result.errorMessage()).doesNotContain(response).doesNotContain(API_KEY).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    static Stream<String> invalidPoiResponses() {
        return Stream.of(
                "{\"status\":\"1\",\"pois\":[{\"name\":\"西湖\",\"address\":\"杭州\",\"location\":\"120.1,30.2\"}]}",
                "{\"status\":\"1\",\"pois\":[{\"id\":\"B1\",\"name\":\"西湖\",\"address\":\"杭州\",\"location\":\"not-a-coordinate\"}]}",
                "{\"status\":\"1\",\"pois\":[{\"id\":\"B1\",\"name\":\"西湖\",\"address\":\"杭州\",\"location\":\"181,30\"}]}",
                "{\"status\":\"1\",\"pois\":{} }",
                "{\"status\":\"0\",\"info\":\"missing infocode\"}",
                "not-json"
        );
    }

    @Test
    void geocodesAddressWithoutInventingPoiId() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/geocode/geo")))
                .andExpect(queryParam("key", API_KEY))
                .andExpect(queryParam("address", "%E6%B9%96%E6%BB%A8%E8%A1%97%E9%81%93"))
                .andExpect(queryParam("city", "%E6%9D%AD%E5%B7%9E"))
                .andExpect(queryParam("output", "JSON"))
                .andRespond(withSuccess("""
                        {"status":"1","count":"1","geocodes":[{
                          "formatted_address":"浙江省杭州市上城区湖滨街道",
                          "location":"120.164520,30.254460"
                        }]}
                        """, MediaType.APPLICATION_JSON));

        ToolResult<PlaceCandidate> result = harness.provider.geocode("杭州", "湖滨街道");

        assertThat(result.success()).isTrue();
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data().poiId()).isNull();
        assertThat(result.data().name()).isEqualTo("湖滨街道");
        assertThat(result.data().address()).isEqualTo("浙江省杭州市上城区湖滨街道");
        assertThat(result.data().location().coordinateSystem()).isEqualTo(CoordinateSystem.GCJ02);
        harness.server.verify();
    }

    @Test
    void mapsEmptyGeocodeResultToPlaceNotFound() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/geocode/geo")))
                .andRespond(withSuccess("{\"status\":\"1\",\"count\":\"0\",\"geocodes\":[]}",
                        MediaType.APPLICATION_JSON));

        ToolResult<PlaceCandidate> result = harness.provider.geocode("杭州", "不存在的地址");

        assertFailure(result, "PLACE_NOT_FOUND");
        harness.server.verify();
    }

    @Test
    void rejectsInvalidInputsBeforeSendingHttpRequest() {
        AmapPlaceProvider provider = noNetworkProvider();

        assertFailure(provider.search(null, "西湖", 10), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.search("杭州", " ", 10), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.search("杭州", "西湖", 0), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.search("杭州", "西湖", 26), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.geocode("杭州", null), "INVALID_PROVIDER_REQUEST");
    }

    @Test
    void mapsAmapHttp200BusinessErrorsWithoutExposingVendorText() {
        Harness searchHarness = harness();
        searchHarness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andRespond(withSuccess(
                        "{\"status\":\"0\",\"info\":\"DAILY_QUERY_OVER_LIMIT secret detail\",\"infocode\":\"10003\"}",
                        MediaType.APPLICATION_JSON));
        ToolResult<List<PlaceCandidate>> rateLimited = searchHarness.provider.search("杭州", "西湖", 10);
        assertFailure(rateLimited, "PROVIDER_RATE_LIMITED");
        assertThat(rateLimited.errorMessage()).doesNotContain("secret detail");
        searchHarness.server.verify();

        Harness geocodeHarness = harness();
        geocodeHarness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/geocode/geo")))
                .andRespond(withSuccess(
                        "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY secret detail\",\"infocode\":\"10001\"}",
                        MediaType.APPLICATION_JSON));
        ToolResult<PlaceCandidate> unavailable = geocodeHarness.provider.geocode("杭州", "湖滨街道");
        assertFailure(unavailable, "PROVIDER_UNAVAILABLE");
        assertThat(unavailable.errorMessage()).doesNotContain("secret detail");
        geocodeHarness.server.verify();
    }

    @Test
    void mapsRateLimitServerFailureAndTimeoutToStableErrors() {
        assertTransportFailure(withStatus(HttpStatus.TOO_MANY_REQUESTS), "PROVIDER_RATE_LIMITED");
        assertTransportFailure(withServerError(), "PROVIDER_UNAVAILABLE");

        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andRespond(request -> { throw new ResourceAccessException("simulated timeout"); });
        ToolResult<List<PlaceCandidate>> timeout = harness.provider.search("杭州", "西湖", 10);
        assertFailure(timeout, "PROVIDER_TIMEOUT");
        assertThat(timeout.errorMessage()).doesNotContain(API_KEY).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    @Test
    void propertiesDoNotExposeApiKey() {
        assertThat(properties().toString()).doesNotContain(API_KEY);
    }

    private void assertTransportFailure(org.springframework.test.web.client.ResponseCreator response,
                                        String expectedCode) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/place/text")))
                .andRespond(response);
        ToolResult<List<PlaceCandidate>> result = harness.provider.search("杭州", "西湖", 10);
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
        return new Harness(server, new AmapPlaceProvider(builder.build(), properties(), CLOCK));
    }

    private static AmapPlaceProvider noNetworkProvider() {
        RestClient client = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, method) -> { throw new AssertionError("HTTP request was not expected"); })
                .build();
        return new AmapPlaceProvider(client, properties(), CLOCK);
    }

    private static AmapProperties properties() {
        return new AmapProperties(BASE_URL, API_KEY, Duration.ofMillis(500), Duration.ofSeconds(2));
    }

    private record Harness(MockRestServiceServer server, AmapPlaceProvider provider) {}
}
