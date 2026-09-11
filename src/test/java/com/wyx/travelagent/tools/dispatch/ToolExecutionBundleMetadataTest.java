package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.WeatherResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionBundleMetadataTest {

    @Test
    void weatherExecutionCanExpressOriginalAndActualRequestCoordinateSystems() {
        WeatherResult weather = new WeatherResult(List.of(new WeatherResult.DailyForecast(
                LocalDate.of(2026, 10, 1), 15, 25, 20, 0)));
        ToolExecutionBundle.Execution execution = new ToolExecutionBundle.Execution(
                TravelToolType.WEATHER_FORECAST,
                ToolResult.success("open-meteo", LocalDateTime.of(2026, 9, 11, 12, 0), weather),
                false, CoordinateSystem.GCJ02, CoordinateSystem.WGS84);

        assertThat(execution.originalCoordinateSystem()).isEqualTo(CoordinateSystem.GCJ02);
        assertThat(execution.requestedCoordinateSystem()).isEqualTo(CoordinateSystem.WGS84);
    }

    @Test
    void nonWeatherExecutionKeepsCoordinateMetadataNotApplicable() {
        ToolExecutionBundle.Execution execution = new ToolExecutionBundle.Execution(
                TravelToolType.SEARCH_POI,
                ToolResult.success("amap", LocalDateTime.of(2026, 9, 11, 12, 0), List.of()),
                false);

        assertThat(execution.originalCoordinateSystem()).isNull();
        assertThat(execution.requestedCoordinateSystem()).isNull();
    }
}
