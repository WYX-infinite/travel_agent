package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.CoordinateSystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务问题：汇总一次受控工具计划的内部结果与实际提供方调用计数，供规划事实转换和 Trace 使用。
 * 输入为按计划顺序排列的执行结果，输出为不可变批次；本类型不保存第三方原始响应、API Key 或认证信息。
 *
 * @param executions 非空且可为空列表的工具执行结果
 * @param providerCallCounts 非空提供方类型到实际调用次数的映射
 */
public record ToolExecutionBundle(List<Execution> executions, Map<String, Integer> providerCallCounts) {
    /** 创建防御复制的工具批次；只处理内存对象，不访问任何外部资源。 */
    public ToolExecutionBundle {
        executions = List.copyOf(executions);
        providerCallCounts = Map.copyOf(new LinkedHashMap<>(providerCallCounts));
    }

    /**
     * 业务问题：记录单个工具的安全结果、缓存命中和天气坐标系边界。
     * 普通工具的两个坐标系均为空；天气工具必须成对记录原始坐标系和实际请求坐标系，但不保存数值坐标。
     *
     * @param type 非空工具类型
     * @param result 非空内部类型化结果
     * @param cacheHit 是否命中缓存
     * @param originalCoordinateSystem 天气地点原始坐标系；非天气或不适用时为空
     * @param requestedCoordinateSystem 实际传给天气提供方的坐标系；与原始坐标系成对出现
     */
    public record Execution(
            TravelToolType type,
            ToolResult<?> result,
            boolean cacheHit,
            CoordinateSystem originalCoordinateSystem,
            CoordinateSystem requestedCoordinateSystem) {

        /**
         * 创建非天气执行结果，坐标元数据明确为不适用。
         *
         * @param type 非空工具类型
         * @param result 非空内部工具结果
         * @param cacheHit 是否命中缓存
         */
        public Execution(TravelToolType type, ToolResult<?> result, boolean cacheHit) {
            this(type, result, cacheHit, null, null);
        }

        /** 创建语义一致的执行快照；只校验内存字段，不访问提供方或缓存。 */
        public Execution {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(result, "result");
            if ((originalCoordinateSystem == null) != (requestedCoordinateSystem == null)) {
                throw new IllegalArgumentException("coordinate systems must be present together");
            }
            if (type != TravelToolType.WEATHER_FORECAST && originalCoordinateSystem != null) {
                throw new IllegalArgumentException("coordinate metadata is only valid for weather");
            }
        }

        /**
         * 给天气执行结果补充原始与实际请求坐标系，不保存精确坐标。
         *
         * @param original 原始坐标系
         * @param requested 实际传给天气 HTTP API 的坐标系
         * @return 带坐标审计元数据的新执行结果
         */
        public Execution withWeatherCoordinates(CoordinateSystem original, CoordinateSystem requested) {
            if (type != TravelToolType.WEATHER_FORECAST) {
                throw new IllegalStateException("weather coordinate metadata on non-weather execution");
            }
            return new Execution(type, result, cacheHit,
                    Objects.requireNonNull(original, "original"),
                    Objects.requireNonNull(requested, "requested"));
        }
    }
}
