package com.wyx.travelagent.tools.port;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.WeatherResult;

import java.time.LocalDate;

public interface WeatherProvider {
    ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate);

    /**
     * 返回实现实际传给天气 HTTP API 的坐标系。
     * 默认实现表示供应商不做转换；会转换的适配器必须覆盖，供 Trace 审计且不得返回坐标值。
     *
     * @param original 非空原始坐标
     * @return 非空实际请求坐标系
     */
    default CoordinateSystem requestCoordinateSystem(GeoPoint original) {
        return original.coordinateSystem();
    }
}
