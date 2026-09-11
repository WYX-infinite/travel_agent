package com.wyx.travelagent.planning.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.TravelRequirementInput;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 业务问题：冻结创建异步规划任务所需的结构化 HTTP 字段，并映射偏好字段三态。
 * 输入来自 JSON，输出不含 userId 的 TravelRequirementInput；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它承接任务十三的输入并属于异步创建接口；不负责接受客户端任务状态、planId 或用户身份，也不执行校验、偏好读取或规划。
 * @param destinationCity 目的城市
 * @param startDate 行程首日
 * @param endDate 行程末日
 * @param travelers 出行人数
 * @param totalBudget 总预算
 * @param companionType 同行类型枚举文本
 * @param interests null 表示未提供，空列表表示显式清空，非空表示显式值
 * @param pace null 表示未提供，空串表示显式清空，其他文本表示显式值
 * @param accommodationAddress null 表示未提供，空串表示显式清空，其他文本表示显式值
 * @param travelMode null 表示未提供，空串表示显式清空，其他文本表示显式值
 * @param specialRequirements 可为空且永不从长期偏好补全
 * @param useSavedPreferences null 表示默认 true，显式 false 必须保留
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TravelPlanningRequest(String destinationCity, LocalDate startDate, LocalDate endDate,
        Integer travelers, BigDecimal totalBudget, String companionType, List<String> interests,
        String pace, String accommodationAddress, String travelMode, String specialRequirements,
        Boolean useSavedPreferences) {
    /** 创建防御复制请求；只转换内存值，不访问外部资源或认证信息。 */
    public TravelPlanningRequest {
        interests = interests == null ? null : Collections.unmodifiableList(new ArrayList<>(interests));
    }

    /**
     * 转换为领域三态输入。
     * @return 非空且不含用户身份的原始需求；字段合法性由 RequirementValidator 统一判断
     * 本方法无输入参数，不访问外部资源，也不处理认证；null 开关原样保留以实现默认 true。
     */
    public TravelRequirementInput toInput() {
        return new TravelRequirementInput(destinationCity, startDate, endDate, travelers, totalBudget,
                companionType, interests == null ? PreferenceValue.notProvided()
                        : interests.isEmpty() ? PreferenceValue.explicitEmpty() : PreferenceValue.explicitValue(interests),
                text(pace), text(accommodationAddress), text(travelMode), specialRequirements, useSavedPreferences);
    }

    private static PreferenceValue<String> text(String value) {
        if (value == null) return PreferenceValue.notProvided();
        if (value.isEmpty()) return PreferenceValue.explicitEmpty();
        return PreferenceValue.explicitValue(value);
    }
}
