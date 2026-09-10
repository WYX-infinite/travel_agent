package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.TravelMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：表示完成偏好合并和完整校验后的强类型旅行需求，避免后续规划重复解释原始输入。
 * 输入是规范化硬约束和可选偏好，输出是确定性不可变需求；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责工具执行，任务十二负责生成后行程校验，后续编排消费本需求；本类型不负责认证、偏好读取、工具调用或行程生成。
 *
 * @param destinationCity 已去除首尾空白的目的城市，不得为空
 * @param startDate 行程首日，包含在行程内
 * @param endDate 行程末日，包含在行程内，首尾合计一至五天
 * @param travelers 正整数出行人数
 * @param totalBudget 大于零的总预算
 * @param companionType 非空同行类型
 * @param interests 确定顺序的不可变兴趣集合，可为空
 * @param pace 可缺失的行程节奏
 * @param accommodationAddress 可缺失的住宿地址；非空时已去除首尾空白
 * @param travelMode 可缺失的交通方式，复用任务十工具模型
 * @param specialRequirements 永不由偏好补全的特殊要求；{@code null} 与显式空串保持不同
 */
public record TravelRequirement(
        String destinationCity,
        LocalDate startDate,
        LocalDate endDate,
        int travelers,
        BigDecimal totalBudget,
        CompanionType companionType,
        Set<TravelInterest> interests,
        TravelPace pace,
        String accommodationAddress,
        TravelMode travelMode,
        String specialRequirements) {

    /**
     * 创建已验证需求的不可变快照。
     * 调用方通常应使用 {@code RequirementValidator}，直接传入违反硬约束的值会抛出异常；不产生任何外部访问。
     * 城市、日期、人数、预算、同行类型及兴趣集合不得为 null；可选偏好可为 null，特殊要求的 null 与显式空串保持不同。
     * 输出会 strip 文本并防御复制兴趣；不访问数据库、模型、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当硬约束或兴趣集合为 {@code null}
     * @throws IllegalArgumentException 当城市空白、人数或预算非正、日期范围不在一至五天内
     */
    public TravelRequirement {
        destinationCity = requireNonBlank(destinationCity, "destinationCity");
        startDate = Objects.requireNonNull(startDate, "startDate");
        endDate = Objects.requireNonNull(endDate, "endDate");
        if (endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) + 1 > 5) {
            throw new IllegalArgumentException("dateRange");
        }
        if (travelers <= 0) {
            throw new IllegalArgumentException("travelers");
        }
        totalBudget = Objects.requireNonNull(totalBudget, "totalBudget");
        if (totalBudget.signum() <= 0) {
            throw new IllegalArgumentException("totalBudget");
        }
        companionType = Objects.requireNonNull(companionType, "companionType");
        interests = immutableInterests(interests);
        if (accommodationAddress != null) {
            accommodationAddress = requireNonBlank(accommodationAddress, "accommodationAddress");
        }
        if (specialRequirements != null) {
            // 特殊要求永不参与偏好补全，但所有显式文本仍采用一致的 strip 规范；空串与 null 继续区分。
            specialRequirements = specialRequirements.strip();
        }
    }

    /**
     * 返回首尾均包含的自然日数量。
     *
     * @return 一至五之间的天数
     * 前置条件由构造器保证，无参数且不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public int durationDays() {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field);
        }
        return value.strip();
    }

    private static Set<TravelInterest> immutableInterests(Set<TravelInterest> source) {
        Objects.requireNonNull(source, "interests");
        if (source.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }
}
