package com.wyx.travelagent.tools.weather;

import com.wyx.travelagent.tools.geo.Gcj02ToWgs84Converter;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.WeatherResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoWeatherProviderTest {

    private static final String BASE_URL = "https://weather.test";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 9, 10, 12, 0);
    private static final LocalDate START = LocalDate.of(2026, 9, 10);
    private static final LocalDate END = LocalDate.of(2026, 9, 11);

    @Test
    void sendsWgs84CoordinatesDateRangeDailyFieldsAndTimezoneAndMapsForecasts() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(queryParam("latitude", "30.274100"))
                .andExpect(queryParam("longitude", "120.155100"))
                .andExpect(queryParam("start_date", "2026-09-10"))
                .andExpect(queryParam("end_date", "2026-09-11"))
                .andExpect(queryParam("daily",
                        "temperature_2m_min,temperature_2m_max,precipitation_probability_max,precipitation_sum"))
                .andExpect(queryParam("timezone", "Asia/Shanghai"))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        ToolResult<WeatherResult> result = harness.provider.forecast(
                new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84), START, END);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo("open-meteo");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
        assertThat(result.data().dailyForecasts()).containsExactly(
                new WeatherResult.DailyForecast(START, 20.5, 28.2, 60, 4.5),
                new WeatherResult.DailyForecast(END, 19.0, 27.0, 20, 0.0));
        assertThatThrownBy(() -> result.data().dailyForecasts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        harness.server.verify();
    }

    @Test
    void convertsMainlandGcj02ExactlyOnceBeforeRequestWithoutMutatingInput() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(queryParam("longitude", "116.397756"))
                .andExpect(queryParam("latitude", "39.913596"))
                .andRespond(withSuccess(oneDayResponse(), MediaType.APPLICATION_JSON));
        GeoPoint source = new GeoPoint(116.404000, 39.915000, CoordinateSystem.GCJ02);

        ToolResult<WeatherResult> result = harness.provider.forecast(source, START, START);

        assertThat(result.success()).isTrue();
        assertThat(source).isEqualTo(new GeoPoint(116.404000, 39.915000, CoordinateSystem.GCJ02));
        harness.server.verify();
    }

    @Test
    void relabelsOutsideMainlandGcj02AsWgs84WithoutApplyingOffset() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andExpect(queryParam("longitude", "2.352200"))
                .andExpect(queryParam("latitude", "48.856600"))
                .andRespond(withSuccess(oneDayResponse(), MediaType.APPLICATION_JSON));

        ToolResult<WeatherResult> result = harness.provider.forecast(
                new GeoPoint(2.3522, 48.8566, CoordinateSystem.GCJ02), START, START);

        assertThat(result.success()).isTrue();
        harness.server.verify();
    }

    @Test
    void returnsDateUnavailableWhenProviderDoesNotCoverEveryRequestedDate() {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(withSuccess(oneDayResponse(), MediaType.APPLICATION_JSON));

        ToolResult<WeatherResult> result = harness.provider.forecast(
                new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84), START, END);

        assertFailure(result, "WEATHER_DATE_UNAVAILABLE");
        harness.server.verify();
    }

    @ParameterizedTest
    @MethodSource("invalidWeatherResponses")
    void mapsMalformedOrInvalidWeatherFactsToStableResponseError(String response) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatCode(() -> {
            ToolResult<WeatherResult> result = harness.provider.forecast(
                    new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84), START, END);
            assertFailure(result, "INVALID_PROVIDER_RESPONSE");
            assertThat(result.errorMessage()).doesNotContain(response).doesNotContain(BASE_URL);
        }).doesNotThrowAnyException();
        harness.server.verify();
    }

    static Stream<String> invalidWeatherResponses() {
        return Stream.of(
                "not-json",
                "{\"daily\":{\"time\":[\"not-a-date\",\"2026-09-11\"],\"temperature_2m_min\":[20,19],\"temperature_2m_max\":[28,27],\"precipitation_probability_max\":[60,20],\"precipitation_sum\":[4.5,0]}}",
                "{\"daily\":{\"time\":[null,\"2026-09-11\"],\"temperature_2m_min\":[20,19],\"temperature_2m_max\":[28,27],\"precipitation_probability_max\":[60,20],\"precipitation_sum\":[4.5,0]}}",
                "{\"daily\":{\"time\":[\"2026-09-10\",\"2026-09-11\"],\"temperature_2m_min\":[20],\"temperature_2m_max\":[28,27],\"precipitation_probability_max\":[60,20],\"precipitation_sum\":[4.5,0]}}",
                responseWith("[\"NaN\",19]", "[28,27]", "[60,20]", "[4.5,0]"),
                responseWith("[20,19]", "[28,27]", "[101,20]", "[4.5,0]"),
                responseWith("[20,19]", "[28,27]", "[60,20]", "[-0.1,0]")
        );
    }

    @Test
    void rejectsInvalidInputsBeforeSendingHttpRequest() {
        OpenMeteoWeatherProvider provider = noNetworkProvider();
        GeoPoint location = new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84);

        assertFailure(provider.forecast(null, START, END), "INVALID_COORDINATE");
        assertFailure(provider.forecast(location, null, END), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.forecast(location, START, null), "INVALID_PROVIDER_REQUEST");
        assertFailure(provider.forecast(location, END, START), "INVALID_PROVIDER_REQUEST");
    }

    @Test
    void rejectsDatesOutsideTheSixteenDayForecastWindowWithoutSendingHttpRequest() {
        OpenMeteoWeatherProvider provider = noNetworkProvider();
        GeoPoint location = new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84);

        assertFailure(provider.forecast(location, START.minusDays(1), START), "WEATHER_DATE_UNAVAILABLE");
        assertFailure(provider.forecast(location, START, START.plusDays(16)), "WEATHER_DATE_UNAVAILABLE");
        assertFailure(provider.forecast(location, LocalDate.MAX, LocalDate.MAX), "WEATHER_DATE_UNAVAILABLE");
    }

    @Test
    void calculatesForecastWindowInTheRequestedShanghaiTimezone() {
        Clock utcClockAfterShanghaiMidnight = Clock.fixed(
                Instant.parse("2026-09-09T16:30:00Z"), ZoneId.of("UTC"));
        OpenMeteoWeatherProvider provider = noNetworkProvider(utcClockAfterShanghaiMidnight);
        GeoPoint location = new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84);

        assertFailure(provider.forecast(
                location, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 9)),
                "WEATHER_DATE_UNAVAILABLE");
    }

    @Test
    void mapsRateLimitServerFailureAndTimeoutToStableErrors() {
        assertTransportFailure(withStatus(HttpStatus.TOO_MANY_REQUESTS), "PROVIDER_RATE_LIMITED");
        assertTransportFailure(withServerError(), "PROVIDER_UNAVAILABLE");

        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(request -> { throw new ResourceAccessException("simulated timeout at " + BASE_URL); });
        ToolResult<WeatherResult> timeout = harness.provider.forecast(
                new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84), START, END);
        assertFailure(timeout, "PROVIDER_TIMEOUT");
        assertThat(timeout.errorMessage()).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    private void assertTransportFailure(org.springframework.test.web.client.ResponseCreator response,
                                        String expectedCode) {
        Harness harness = harness();
        harness.server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v1/forecast")))
                .andRespond(response);
        ToolResult<WeatherResult> result = harness.provider.forecast(
                new GeoPoint(120.1551, 30.2741, CoordinateSystem.WGS84), START, END);
        assertFailure(result, expectedCode);
        assertThat(result.errorMessage()).doesNotContain(BASE_URL);
        harness.server.verify();
    }

    private static String validResponse() {
        return responseWith("[20.5,19.0]", "[28.2,27.0]", "[60,20]", "[4.5,0.0]");
    }

    private static String oneDayResponse() {
        return """
                {"daily":{
                  "time":["2026-09-10"],
                  "temperature_2m_min":[20.5],
                  "temperature_2m_max":[28.2],
                  "precipitation_probability_max":[60],
                  "precipitation_sum":[4.5]
                }}
                """;
    }

    private static String responseWith(String minimum, String maximum, String probability, String precipitation) {
        return "{\"daily\":{" +
                "\"time\":[\"2026-09-10\",\"2026-09-11\"]," +
                "\"temperature_2m_min\":" + minimum + "," +
                "\"temperature_2m_max\":" + maximum + "," +
                "\"precipitation_probability_max\":" + probability + "," +
                "\"precipitation_sum\":" + precipitation + "}}";
    }

    private static void assertFailure(ToolResult<?> result, String expectedCode) {
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(expectedCode);
        assertThat(result.data()).isNull();
    }

    private static Harness harness() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(server, new OpenMeteoWeatherProvider(
                builder.build(), new Gcj02ToWgs84Converter(), CLOCK));
    }

    private static OpenMeteoWeatherProvider noNetworkProvider() {
        return noNetworkProvider(CLOCK);
    }

    private static OpenMeteoWeatherProvider noNetworkProvider(Clock clock) {
        RestClient client = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, method) -> { throw new AssertionError("HTTP request was not expected"); })
                .build();
        return new OpenMeteoWeatherProvider(client, new Gcj02ToWgs84Converter(), clock);
    }

    private record Harness(MockRestServiceServer server, OpenMeteoWeatherProvider provider) {}
}
