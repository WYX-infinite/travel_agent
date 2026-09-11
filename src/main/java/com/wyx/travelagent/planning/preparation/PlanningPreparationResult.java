package com.wyx.travelagent.planning.preparation;

import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.tools.model.CoordinateSystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务问题：冻结生成前可信事实和不含第三方正文的工具客观摘要。
 * 输入为内部事实和工具计数，输出为不可变准备结果；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三只传递生成与校验共用快照；本类型不负责调用工具、补造缺失天气或持久化。
 *
 * @param facts 非空生成/校验共用事实
 * @param toolSummary 非空缓存、调用与坐标摘要
 */
public record PlanningPreparationResult(
        ItineraryValidationFacts facts,
        ToolSummary toolSummary) {

    /** 创建不可变准备结果；只校验内存对象，不访问外部资源，也不处理认证。 */
    public PlanningPreparationResult {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(toolSummary, "toolSummary");
    }

    /**
     * 业务问题：提供工具阶段可安全进入 Trace 的计数与坐标系，不保存坐标值或供应商原文。
     * 输入为内部客观计数，输出为不可变脱敏摘要；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
     * 任务十三只把本摘要写入 Trace；本类型不负责采集调用、换算坐标或保存精确经纬度。
     *
     * @param cacheHitCount 成功缓存命中次数
     * @param providerCallCounts 按内部固定提供方类型统计的实际调用次数
     * @param weatherCoordinates 天气调用的原始与实际请求坐标系
     * @param weatherAvailable 是否至少有一份可用天气事实
     */
    public record ToolSummary(
            int cacheHitCount,
            Map<String, Integer> providerCallCounts,
            List<WeatherCoordinateSummary> weatherCoordinates,
            boolean weatherAvailable) {

        /** 创建防御复制摘要；只校验内存计数，不访问外部资源，也不处理认证。 */
        public ToolSummary {
            if (cacheHitCount < 0) throw new IllegalArgumentException("cacheHitCount");
            Objects.requireNonNull(providerCallCounts, "providerCallCounts");
            providerCallCounts = Map.copyOf(new LinkedHashMap<>(providerCallCounts));
            if (providerCallCounts.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("providerCallCounts");
            }
            weatherCoordinates = List.copyOf(Objects.requireNonNull(
                    weatherCoordinates, "weatherCoordinates"));
        }

        /** @return 没有工具调用的明确空摘要；本方法不访问外部资源，也不处理认证。 */
        public static ToolSummary empty() {
            return new ToolSummary(0, Map.of(), List.of(), false);
        }
    }

    /**
     * 业务问题：只记录天气地点进入提供方前后的坐标系，避免把精确坐标写入 Trace。
     * 输入为原始和实际请求坐标系，输出为不可变坐标系对；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
     * 任务十三只消费坐标系名称；本类型不负责坐标转换、网络请求或保存经纬度。
     *
     * @param original 原始地点坐标系
     * @param requested 实际传给天气 HTTP API 的坐标系
     */
    public record WeatherCoordinateSummary(CoordinateSystem original, CoordinateSystem requested) {
        /** 创建非空坐标系对；不保存精确坐标值，不访问外部资源，也不处理认证。 */
        public WeatherCoordinateSummary {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(requested, "requested");
        }
    }
}
