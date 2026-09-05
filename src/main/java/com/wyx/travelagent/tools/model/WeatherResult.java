package com.wyx.travelagent.tools.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record WeatherResult(List<DailyForecast> dailyForecasts) {
    public WeatherResult {
        Objects.requireNonNull(dailyForecasts, "dailyForecasts");
        if (dailyForecasts.isEmpty()) {
            throw new IllegalArgumentException("dailyForecasts must not be empty");
        }
        dailyForecasts = List.copyOf(dailyForecasts);
    }

    public record DailyForecast(
            LocalDate date,
            double minimumTemperatureCelsius,
            double maximumTemperatureCelsius,
            int precipitationProbabilityPercent,
            double precipitationMillimeters
    ) {
        public DailyForecast {
            Objects.requireNonNull(date, "date");
            if (!Double.isFinite(minimumTemperatureCelsius)
                    || !Double.isFinite(maximumTemperatureCelsius)) {
                throw new IllegalArgumentException("temperature must be finite");
            }
            if (minimumTemperatureCelsius > maximumTemperatureCelsius) {
                throw new IllegalArgumentException("minimum temperature cannot exceed maximum temperature");
            }
            if (precipitationProbabilityPercent < 0 || precipitationProbabilityPercent > 100) {
                throw new IllegalArgumentException("precipitation probability out of range");
            }
            if (!Double.isFinite(precipitationMillimeters) || precipitationMillimeters < 0) {
                throw new IllegalArgumentException("precipitation must be a non-negative finite value");
            }
        }
    }
}
