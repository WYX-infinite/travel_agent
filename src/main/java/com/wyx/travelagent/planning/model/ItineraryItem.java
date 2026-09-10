package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.TravelMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：以明确字段表示某日行程活动，避免无约束 Map 进入行程结果。
 * 输入是时间、POI、说明、带证据费用和交通方式，输出是不可变条目；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责 POI 与路线数据，任务十二负责时序、路线和费用校验，后续编排组合条目；本类型不负责认证、工具调用、冲突判断或自动修正。
 *
 * @param startTime 活动开始时间，不得为 {@code null}
 * @param endTime 活动结束时间，不得为 {@code null}
 * @param poiId 工具层返回的 POI 标识，不得为 {@code null} 或空白
 * @param name 活动或地点名称，不得为 {@code null} 或空白
 * @param description 可选说明，可为 {@code null}
 * @param cost 带来源的费用区间或明确缺失状态，不得为 {@code null}
 * @param travelModeFromPrevious 从前一条目到达本条目的交通方式；首条目可为 {@code null}
 */
public record ItineraryItem(
        LocalTime startTime,
        LocalTime endTime,
        String poiId,
        String name,
        String description,
        CostEstimate cost,
        TravelMode travelModeFromPrevious) {

    static final Comparator<ItineraryItem> DETERMINISTIC_ORDER = Comparator
            .comparing(ItineraryItem::startTime)
            .thenComparing(ItineraryItem::endTime)
            .thenComparing(ItineraryItem::poiId)
            .thenComparing(ItineraryItem::name)
            .thenComparing(ItineraryItem::description, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ItineraryItem::cost, CostEstimate.DETERMINISTIC_ORDER)
            .thenComparing(ItineraryItem::travelModeFromPrevious,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * 创建不可变行程条目。
     * 此处不判断活动重叠或路线可达性，避免越过任务十二校验器边界。
     * 时间、POI、名称和费用不得为 null；说明及首条交通方式可为 null，费用自身保证区间和证据合法。
     * 输出文本会 strip；不访问数据库、模型、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当时间、POI、名称或费用为 {@code null}
     * @throws IllegalArgumentException 当 POI 或名称空白
     */
    public ItineraryItem {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        poiId = requireNonBlank(poiId, "poiId");
        name = requireNonBlank(name, "name");
        Objects.requireNonNull(cost, "cost");
        if (description != null) description = description.strip();
    }

    /**
     * 使用任务十一旧签名创建 AI 估算单值费用，保持既有调用源码兼容。
     *
     * @param startTime 非空开始时间
     * @param endTime 非空结束时间
     * @param poiId 非空白 POI ID
     * @param name 非空白名称
     * @param description 可为 {@code null} 的说明
     * @param estimatedCost 非空且非负的旧单值费用
     * @param travelModeFromPrevious 可为 {@code null} 的到达方式
     * @throws NullPointerException 当必要参数为空
     * @throws IllegalArgumentException 当文本空白或费用为负
     * 无返回值；旧单值会转换为上下限相等的 AI_ESTIMATE。本构造器不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public ItineraryItem(
            LocalTime startTime,
            LocalTime endTime,
            String poiId,
            String name,
            String description,
            BigDecimal estimatedCost,
            TravelMode travelModeFromPrevious) {
        this(startTime, endTime, poiId, name, description,
                CostEstimate.known(estimatedCost, estimatedCost, CostEvidenceType.AI_ESTIMATE, null),
                travelModeFromPrevious);
    }

    /**
     * 返回任务十一兼容的保守单值费用。
     *
     * @return 已知费用的上限；费用缺失时返回零但不得据此声称预算完整
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public BigDecimal estimatedCost() {
        return cost.isKnown() ? cost.maximum() : BigDecimal.ZERO;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }
}
