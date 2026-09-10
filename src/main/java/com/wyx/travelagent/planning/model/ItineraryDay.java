package com.wyx.travelagent.planning.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：表示一个自然日及确定顺序的条目，避免上游集合顺序造成不稳定结果。
 * 输入是日期与条目列表，输出是排序且不可变的日行程；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十提供 POI/路线数据，任务十二负责冲突与营业时间校验，后续编排组合每日结果；本类型不负责认证、生成、可达性判断或修正。
 *
 * @param date 行程日期，不得为 {@code null}
 * @param items 当日条目；不得为 {@code null} 或包含 {@code null}，输出按条目全部业务字段形成稳定全序
 */
public record ItineraryDay(LocalDate date, List<ItineraryItem> items) {

    static final Comparator<ItineraryDay> DETERMINISTIC_ORDER = Comparator
            .comparing(ItineraryDay::date)
            .thenComparing(ItineraryDay::items, ItineraryDay::compareItemLists);

    /**
     * 创建不可变日行程。
     * 日期和列表不得为 null，列表不得含 null，空列表允许且保持显式空；输出按条目的全部字段形成 null-safe 稳定全序。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证；仅前置条件失败时抛出参数异常。
     *
     * @throws NullPointerException 当日期、列表或条目为 {@code null}
     */
    public ItineraryDay {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(items, "items");
        items.forEach(item -> Objects.requireNonNull(item, "item"));
        // 稳定排序让相同业务输入不受上游集合迭代顺序影响；更复杂时序规则留给任务十二。
        items = items.stream().sorted(ItineraryItem.DETERMINISTIC_ORDER).toList();
    }

    private static int compareItemLists(List<ItineraryItem> left, List<ItineraryItem> right) {
        int sharedSize = Math.min(left.size(), right.size());
        for (int index = 0; index < sharedSize; index++) {
            int comparison = ItineraryItem.DETERMINISTIC_ORDER.compare(left.get(index), right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
