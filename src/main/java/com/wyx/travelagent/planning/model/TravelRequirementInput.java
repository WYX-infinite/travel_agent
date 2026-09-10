package com.wyx.travelagent.planning.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 业务问题：承载规划入口原始需求，并保留偏好字段的未提供、显式空和显式值三态，避免错误补全。
 * 输入是外部文本、日期、数字及三态偏好，输出是防御复制后的不可变输入；本类型不访问数据库、模型、缓存、文件或网络，不处理认证，也不含用户标识。
 * 任务十负责工具调用，任务十二负责行程校验，后续编排消费已验证需求；本类型不负责偏好读取、认证、行程生成或外部枚举的最终校验。
 *
 * @param destinationCity 目的城市；{@code null}、空串和空白由校验器稳定拒绝
 * @param startDate 首日，包含在行程内，可为 {@code null} 并由校验器报告
 * @param endDate 末日，包含在行程内，可为 {@code null} 并由校验器报告
 * @param travelers 出行人数，可为 {@code null} 并由校验器报告
 * @param totalBudget 总预算，可为 {@code null} 并由校验器报告
 * @param companionType 外部同行类型字符串，可为 {@code null} 并由校验器报告
 * @param interests 兴趣字符串集合的三态输入；{@code null} 等同未提供，显式空禁止偏好覆盖
 * @param pace 行程节奏字符串的三态输入；{@code null} 等同未提供
 * @param accommodationAddress 住宿地址的三态输入；空白显式值不会被降级为未提供
 * @param travelMode 交通方式字符串的三态输入；最终复用工具层 {@code TravelMode}
 * @param specialRequirements 特殊要求，可为 {@code null}，永不参与偏好补全
 * @param useSavedPreferences 是否使用已保存偏好；仅 {@link Boolean#FALSE} 明确关闭
 */
public record TravelRequirementInput(
        String destinationCity,
        LocalDate startDate,
        LocalDate endDate,
        Integer travelers,
        BigDecimal totalBudget,
        String companionType,
        PreferenceValue<List<String>> interests,
        PreferenceValue<String> pace,
        PreferenceValue<String> accommodationAddress,
        PreferenceValue<String> travelMode,
        String specialRequirements,
        Boolean useSavedPreferences) {

    /**
     * 创建不可变规划输入。
     * 集合在此防御复制并按去空白后的文本排序，使调用方修改或集合迭代顺序不会改变规划结果；校验仍在偏好合并后统一执行。
     * 硬约束允许暂存 null 交给校验器；null 偏好包装视为未提供，显式空保持禁止覆盖，显式空白文本仍是显式值。
     * 构造输出不含用户标识，不访问数据库、模型、缓存、文件或网络，不处理认证；显式值容器为 null 时由工厂先行拒绝。
     */
    public TravelRequirementInput {
        interests = copyAndOrderInterests(interests);
        pace = defaultState(pace);
        accommodationAddress = defaultState(accommodationAddress);
        travelMode = defaultState(travelMode);
    }

    /**
     * 计算是否允许读取已保存偏好。
     *
     * @return 除显式 {@code false} 外均为 {@code true}
     * 无参数、无业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证；null 保持默认开启语义。
     */
    public boolean shouldUseSavedPreferences() {
        return !Boolean.FALSE.equals(useSavedPreferences);
    }

    /**
     * @param value 新目的城市，可为 {@code null}、空串或空白，随后由校验器报告
     * @return 仅替换该字段的新不可变输入
     * 不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withDestinationCity(String value) {
        return copy(value, startDate, endDate, travelers, totalBudget, companionType,
                interests, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param start 新首日，可为 {@code null}
     * @param end 新末日，可为 {@code null}
     * @return 仅替换日期的新不可变输入
     * 不校验日期先后且不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withDates(LocalDate start, LocalDate end) {
        return copy(destinationCity, start, end, travelers, totalBudget, companionType,
                interests, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新人数，可为 {@code null} 或非正数，随后由校验器报告
     * @return 仅替换该字段的新不可变输入
     * 不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withTravelers(Integer value) {
        return copy(destinationCity, startDate, endDate, value, totalBudget, companionType,
                interests, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新预算，可为 {@code null} 或非正数，随后由校验器报告
     * @return 仅替换该字段的新不可变输入
     * 不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withTotalBudget(BigDecimal value) {
        return copy(destinationCity, startDate, endDate, travelers, value, companionType,
                interests, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新同行类型外部值，可为 {@code null}、空白或未知值，随后由校验器报告
     * @return 仅替换该字段的新不可变输入
     * 不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withCompanionType(String value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, value,
                interests, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新兴趣三态；{@code null} 表示未提供，显式空不得被偏好覆盖
     * @return 仅替换该字段且已防御复制的新输入
     * 显式值集合会 strip、按大小写无关枚举语义规范化、排序和去重；不访问外部资源或认证信息，未知值留给校验器报告。
     */
    public TravelRequirementInput withInterests(PreferenceValue<List<String>> value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                value, pace, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新节奏三态，{@code null} 表示未提供，显式空禁止覆盖
     * @return 仅替换该字段的新不可变输入
     * 显式空白仍是显式值；不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withPace(PreferenceValue<String> value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                interests, value, accommodationAddress, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新住宿地址三态；空白显式值仍是显式值并会被校验器拒绝
     * @return 仅替换该字段的新输入
     * null 表示未提供，显式空禁止覆盖；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withAccommodationAddress(PreferenceValue<String> value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                interests, pace, value, travelMode, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新交通方式三态，{@code null} 表示未提供，显式空禁止覆盖
     * @return 仅替换该字段的新不可变输入
     * 未知或空白显式值留给校验器报告；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withTravelMode(PreferenceValue<String> value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                interests, pace, accommodationAddress, value, specialRequirements, useSavedPreferences);
    }

    /**
     * @param value 新特殊要求，可为 {@code null}；不会参与保存偏好补全
     * @return 仅替换该字段的新输入
     * 空串与 null 原样区分，不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withSpecialRequirements(String value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                interests, pace, accommodationAddress, travelMode, value, useSavedPreferences);
    }

    /**
     * @param value 新开关；{@code null} 保留未提供语义，只有 {@code false} 关闭补全
     * @return 仅替换该字段的新输入
     * 不抛业务异常，不读取偏好，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public TravelRequirementInput withUseSavedPreferences(Boolean value) {
        return copy(destinationCity, startDate, endDate, travelers, totalBudget, companionType,
                interests, pace, accommodationAddress, travelMode, specialRequirements, value);
    }

    private TravelRequirementInput copy(
            String city, LocalDate start, LocalDate end, Integer people, BigDecimal budget, String companion,
            PreferenceValue<List<String>> interestValues, PreferenceValue<String> paceValue,
            PreferenceValue<String> address, PreferenceValue<String> mode, String special, Boolean saved) {
        return new TravelRequirementInput(city, start, end, people, budget, companion,
                interestValues, paceValue, address, mode, special, saved);
    }

    private static PreferenceValue<List<String>> copyAndOrderInterests(PreferenceValue<List<String>> source) {
        PreferenceValue<List<String>> effective = defaultState(source);
        if (effective.state() != PreferenceValue.State.EXPLICIT_VALUE) {
            return effective;
        }
        List<String> ordered = new ArrayList<>(effective.value().size());
        for (String raw : effective.value()) {
            ordered.add(raw == null ? null : raw.strip().toUpperCase(Locale.ROOT));
        }
        // 与枚举解析使用同一规范形式，避免 FOOD/food 被当作两个不同兴趣；是否支持该值仍由校验器决定。
        ordered.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        List<String> unique = new ArrayList<>(ordered.size());
        for (String candidate : ordered) {
            if (unique.isEmpty() || !java.util.Objects.equals(unique.getLast(), candidate)) {
                unique.add(candidate);
            }
        }
        return PreferenceValue.explicitValue(Collections.unmodifiableList(unique));
    }

    private static <T> PreferenceValue<T> defaultState(PreferenceValue<T> value) {
        return value == null ? PreferenceValue.notProvided() : value;
    }
}
