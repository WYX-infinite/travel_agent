package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.PlaceCandidate;

import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：给本次允许 POI 增加稳定优先级、必需性、户外属性和营业时间，供生成与校验共享。
 * 输入是任务十内部地点及确定性元数据，输出为不可变事实；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供 PlaceCandidate，任务十二执行白名单与事实校验，后续编排可选择候选；本类型不负责搜索地点、推断户外属性或自动修正。
 *
 * @param place 非空且具有真实非空 poiId 的内部地点
 * @param priority 非负优先级，数值越小越优先
 * @param required 是否为不可裁剪的必要地点事实
 * @param outdoor 是否由可信结构化规则标记为户外
 * @param openingHours 非空营业时间事实，可明确为未知
 */
public record ValidationPoiFact(
        PlaceCandidate place,
        int priority,
        boolean required,
        boolean outdoor,
        OpeningHoursFact openingHours) {

    static final Comparator<ValidationPoiFact> DETERMINISTIC_ORDER = Comparator
            .comparing(ValidationPoiFact::required).reversed()
            .thenComparingInt(ValidationPoiFact::priority)
            .thenComparing(fact -> fact.place().poiId());

    /**
     * 创建单个不可变 POI 验证事实。
     * 地点、POI ID 和营业时间不得为空，优先级不得为负；户外与必需标记由可信上游显式提供。
     * 构造只校验内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当地点、poiId 或营业事实为空
     * @throws IllegalArgumentException 当 poiId 为空白或优先级为负
     */
    public ValidationPoiFact {
        Objects.requireNonNull(place, "place");
        if (place.poiId() == null || place.poiId().isBlank()) {
            throw new IllegalArgumentException("place must have poiId");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority");
        }
        Objects.requireNonNull(openingHours, "openingHours");
    }
}
