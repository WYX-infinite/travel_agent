package com.wyx.travelagent.tools.port;

import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.WeatherResult;

import java.time.LocalDate;

public interface WeatherProvider {
    ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate);
}
