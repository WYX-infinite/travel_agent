package com.wyx.travelagent.planning.validation;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：以安全、稳定且可排序的结构表达单个行程问题，禁止把用户全文或底层异常写入消息。
 * 输入为规则代码、级别、定位字段和必要事实，输出为不可变问题；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供已净化事实，任务十二创建并排序问题，后续任务十三只能消费结构化问题；本类型不负责模型修正、日志或 HTTP 映射。
 *
 * @param code 非空白稳定问题码
 * @param severity 非空 ERROR 或 WARNING
 * @param date 可缺失日期；{@code null} 表示问题属于计划整体
 * @param itemIndex 可缺失的当日零基条目序号；{@code null} 表示无法定位单项
 * @param poiId 可缺失的相关 POI ID，不得包含完整用户文本
 * @param message 非空白固定安全说明
 * @param knownValue 可缺失的已知事实摘要
 * @param requiredAdjustment 可缺失的必要调整量
 */
public record ValidationIssue(
        String code,
        IssueSeverity severity,
        LocalDate date,
        Integer itemIndex,
        String poiId,
        String message,
        String knownValue,
        String requiredAdjustment) {

    static final Comparator<ValidationIssue> DETERMINISTIC_ORDER = Comparator
            .comparingInt((ValidationIssue issue) -> issue.severity() == IssueSeverity.ERROR ? 0 : 1)
            .thenComparing(ValidationIssue::date, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ValidationIssue::itemIndex, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ValidationIssue::poiId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ValidationIssue::code);

    /**
     * 创建安全不可变问题。
     * code、severity 和 message 必填；itemIndex 非空时不得为负；可选文本会去除首尾空白且不能显式为空。
     * 构造只校验内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当 code、severity 或 message 为空
     * @throws IllegalArgumentException 当必填文本空白、序号为负或可选文本为空白
     */
    public ValidationIssue {
        code = requireText(code, "code");
        Objects.requireNonNull(severity, "severity");
        message = requireText(message, "message");
        if (itemIndex != null && itemIndex < 0) throw new IllegalArgumentException("itemIndex");
        poiId = optionalText(poiId, "poiId");
        knownValue = optionalText(knownValue, "knownValue");
        requiredAdjustment = optionalText(requiredAdjustment, "requiredAdjustment");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }

    private static String optionalText(String value, String field) {
        if (value == null) return null;
        return requireText(value, field);
    }
}
