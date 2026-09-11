package com.wyx.travelagent.tools.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wyx.travelagent.tools.geo.Gcj02ToWgs84Converter;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.WeatherResult;
import com.wyx.travelagent.tools.port.WeatherProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class OpenMeteoWeatherProvider implements WeatherProvider {
    private static final String PROVIDER = "open-meteo";
    private static final String DAILY_FIELDS = String.join(",",
            "temperature_2m_min",
            "temperature_2m_max",
            "precipitation_probability_max",
            "precipitation_sum");
    private static final String TIMEZONE = "Asia/Shanghai";
    private static final ZoneId FORECAST_ZONE = ZoneId.of(TIMEZONE);

    private final RestClient restClient;
    private final Gcj02ToWgs84Converter converter;
    private final Clock clock;

    public OpenMeteoWeatherProvider(RestClient restClient, Gcj02ToWgs84Converter converter, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate) {
        if (location == null) {
            return failure("INVALID_COORDINATE", "weather coordinate is required");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return failure("INVALID_PROVIDER_REQUEST", "weather date range is invalid");
        }
        LocalDate today = LocalDate.now(clock.withZone(FORECAST_ZONE));
        LocalDate lastForecastDate = today.plusDays(15);
        if (startDate.isBefore(today) || endDate.isAfter(lastForecastDate)) {
            return failure("WEATHER_DATE_UNAVAILABLE", "weather is unavailable for part of the date range");
        }

        try {
            GeoPoint requestLocation = converter.convert(location);
            WeatherResponse response = restClient.get()
                    .uri(builder -> builder.path("/v1/forecast")
                            .queryParam("latitude", coordinate(requestLocation.latitude()))
                            .queryParam("longitude", coordinate(requestLocation.longitude()))
                            .queryParam("start_date", startDate)
                            .queryParam("end_date", endDate)
                            .queryParam("daily", DAILY_FIELDS)
                            .queryParam("timezone", TIMEZONE)
                            .build())
                    .retrieve()
                    .body(WeatherResponse.class);
            return mapResponse(response, startDate, endDate);
        } catch (ResourceAccessException exception) {
            return failure("PROVIDER_TIMEOUT", "weather provider timed out");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return failure("PROVIDER_RATE_LIMITED", "weather provider rate limit exceeded");
            }
            return failure("PROVIDER_UNAVAILABLE", "weather provider is unavailable");
        } catch (RestClientException | IllegalArgumentException exception) {
            return failure("INVALID_PROVIDER_RESPONSE", "weather provider returned an invalid response");
        }
    }

    /**
     * Open-Meteo HTTP 请求始终使用 WGS84；GCJ02 仅在适配器内部转换一次。
     *
     * @param original 非空原始坐标
     * @return 固定 WGS84
     */
    @Override
    public CoordinateSystem requestCoordinateSystem(GeoPoint original) {
        Objects.requireNonNull(original, "original");
        return CoordinateSystem.WGS84;
    }

    private ToolResult<WeatherResult> mapResponse(WeatherResponse response, LocalDate startDate, LocalDate endDate) {
        if (response == null || response.daily() == null || !sameArrayLengths(response.daily())) {
            return failure("INVALID_PROVIDER_RESPONSE", "weather provider returned an invalid response");
        }

        List<LocalDate> dates = parseDates(response.daily().time());
        List<LocalDate> expectedDates = startDate.datesUntil(endDate.plusDays(1)).toList();
        if (!dates.equals(expectedDates)) {
            return failure("WEATHER_DATE_UNAVAILABLE", "weather is unavailable for part of the date range");
        }

        List<WeatherResult.DailyForecast> forecasts = new ArrayList<>(dates.size());
        for (int index = 0; index < dates.size(); index++) {
            forecasts.add(new WeatherResult.DailyForecast(
                    dates.get(index),
                    required(response.daily().minimumTemperatures().get(index)),
                    required(response.daily().maximumTemperatures().get(index)),
                    required(response.daily().precipitationProbabilities().get(index)),
                    required(response.daily().precipitationTotals().get(index))));
        }
        return ToolResult.success(PROVIDER, now(), new WeatherResult(forecasts));
    }

    private static boolean sameArrayLengths(DailyResponse daily) {
        if (daily.time() == null
                || daily.minimumTemperatures() == null
                || daily.maximumTemperatures() == null
                || daily.precipitationProbabilities() == null
                || daily.precipitationTotals() == null) {
            return false;
        }
        int size = daily.time().size();
        return daily.minimumTemperatures().size() == size
                && daily.maximumTemperatures().size() == size
                && daily.precipitationProbabilities().size() == size
                && daily.precipitationTotals().size() == size;
    }

    private static List<LocalDate> parseDates(List<String> rawDates) {
        List<LocalDate> dates = new ArrayList<>(rawDates.size());
        for (String rawDate : rawDates) {
            if (rawDate == null) {
                throw new IllegalArgumentException("missing forecast date");
            }
            try {
                dates.add(LocalDate.parse(rawDate));
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("invalid forecast date", exception);
            }
        }
        return List.copyOf(dates);
    }

    private static double required(Double value) {
        if (value == null) {
            throw new IllegalArgumentException("missing decimal fact");
        }
        return value;
    }

    private static int required(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("missing integer fact");
        }
        return value;
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private ToolResult<WeatherResult> failure(String code, String message) {
        return ToolResult.failure(PROVIDER, now(), code, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record WeatherResponse(DailyResponse daily) {}

    private record DailyResponse(
            List<String> time,
            @JsonProperty("temperature_2m_min") List<Double> minimumTemperatures,
            @JsonProperty("temperature_2m_max") List<Double> maximumTemperatures,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilities,
            @JsonProperty("precipitation_sum") List<Double> precipitationTotals
    ) {}
}
