package com.wyx.travelagent.planning.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：表示生成阶段的类型化行程计划，确保日期顺序和费用结构确定。
 * 输入是城市、日期范围、日行程及总费用，输出是不可变计划；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十提供工具数据，任务十二负责完整性校验，后续编排消费或修正计划；本类型不负责认证、生成、日期覆盖判断、修正或追踪。
 *
 * @param destinationCity 目的城市，不得为 {@code null} 或空白
 * @param startDate 行程首日，不得为 {@code null}
 * @param endDate 行程末日，不得为 {@code null}
 * @param days 日行程列表；不得为 {@code null} 或包含 {@code null}，输出按日期及当日内容形成稳定全序
 * @param estimatedTotalCost 总预计费用，不得为 {@code null} 或负数
 */
public record ItineraryPlan(
        String destinationCity,
        LocalDate startDate,
        LocalDate endDate,
        List<ItineraryDay> days,
        BigDecimal estimatedTotalCost) {

    /**
     * 创建不可变行程计划。
     * 仅维护基本类型和顺序不变量，不判断天数覆盖或条目业务可行性。
     * 城市、日期、列表和费用不得为 null；城市不得显式空白，日列表可显式为空，费用允许零但不得为负。
     * 输出城市会 strip、日列表会防御复制并按日期及当日条目内容排序；不访问数据库、模型、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当必要字段、列表或列表元素为 {@code null}
     * @throws IllegalArgumentException 当城市空白或费用为负数
     */
    public ItineraryPlan {
        Objects.requireNonNull(destinationCity, "destinationCity");
        destinationCity = destinationCity.strip();
        if (destinationCity.isEmpty()) {
            throw new IllegalArgumentException("destinationCity");
        }
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(days, "days");
        days.forEach(day -> Objects.requireNonNull(day, "day"));
        days = days.stream().sorted(ItineraryDay.DETERMINISTIC_ORDER).toList();
        Objects.requireNonNull(estimatedTotalCost, "estimatedTotalCost");
        if (estimatedTotalCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedTotalCost");
        }
    }
}
