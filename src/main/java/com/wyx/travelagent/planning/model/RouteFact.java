package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.TravelSegment;

import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：用 POI ID 对关联任务十路线事实，避免按名称或集合位置猜测通行关系。
 * 输入为起终点白名单 ID 和内部 TravelSegment，输出为不可变路线事实；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十产生路线结果，任务十二校验间隔，后续编排可复用；本类型不负责调用地图、补造时长或自动修正。
 *
 * @param fromPoiId 非空起点 POI ID
 * @param toPoiId 非空终点 POI ID
 * @param segment 非空内部路线事实，可明确标记未验证
 */
public record RouteFact(String fromPoiId, String toPoiId, TravelSegment segment) {
    static final Comparator<RouteFact> DETERMINISTIC_ORDER = Comparator
            .comparing(RouteFact::fromPoiId)
            .thenComparing(RouteFact::toPoiId)
            .thenComparing(fact -> fact.segment().mode());

    /**
     * 创建不可变路线关联。
     * 起终点 ID 必须非空白且路线对象非空；未验证路线不允许在本类型中补造时间。
     * 构造只校验内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当 ID 或路线为空
     * @throws IllegalArgumentException 当 ID 为空白
     */
    public RouteFact {
        fromPoiId = requireText(fromPoiId, "fromPoiId");
        toPoiId = requireText(toPoiId, "toPoiId");
        Objects.requireNonNull(segment, "segment");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }
}
