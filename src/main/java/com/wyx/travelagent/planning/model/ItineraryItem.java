package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.TravelMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：以明确字段表示某日行程活动，避免无约束 Map 进入行程结果。
 * 输入是时间、POI、说明、费用和交通方式，输出是不可变条目；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责 POI 与路线数据，任务十二负责时序和路线校验，后续编排组合条目；本类型不负责认证、工具调用、冲突判断或修正。
 *
 * @param startTime 活动开始时间，不得为 {@code null}
 * @param endTime 活动结束时间，不得为 {@code null}
 * @param poiId 工具层返回的 POI 标识，不得为 {@code null} 或空白
 * @param name 活动或地点名称，不得为 {@code null} 或空白
 * @param description 可选说明，可为 {@code null}
 * @param estimatedCost 预计费用，不得为 {@code null} 或负数
 * @param travelModeFromPrevious 从前一条目到达本条目的交通方式；首条目可为 {@code null}
 */
public record ItineraryItem(
        LocalTime startTime,
        LocalTime endTime,
        String poiId,
        String name,
        String description,
        BigDecimal estimatedCost,
        TravelMode travelModeFromPrevious) {

    static final Comparator<ItineraryItem> DETERMINISTIC_ORDER = Comparator
            .comparing(ItineraryItem::startTime)
            .thenComparing(ItineraryItem::endTime)
            .thenComparing(ItineraryItem::poiId)
            .thenComparing(ItineraryItem::name)
            .thenComparing(ItineraryItem::description,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ItineraryItem::estimatedCost)
            .thenComparingInt(item -> item.estimatedCost().scale())
            .thenComparing(ItineraryItem::travelModeFromPrevious,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * 创建不可变行程条目。
     * 此处不判断活动重叠或路线可达性，避免越过任务十二校验器边界。
     * 时间、POI、名称和费用不得为 null；说明及首条交通方式可为 null，说明显式空会保留为空串，费用允许零但不得为负。
     * 输出文本会 strip；不访问数据库、模型、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当时间、POI、名称或费用为 {@code null}
     * @throws IllegalArgumentException 当 POI/名称空白或费用为负数
     */
    public ItineraryItem {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        poiId = requireNonBlank(poiId, "poiId");
        name = requireNonBlank(name, "name");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        if (estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost");
        }
        if (description != null) {
            description = description.strip();
        }
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field);
        }
        return normalized;
    }
}
