package com.wyx.travelagent.planning.model;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：用金额区间和证据类型表达一笔费用，并显式表示资料缺失。
 * 输入是费用状态、非负上下限、来源类型及可选知识切片，输出为不可变费用；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十工具事实与知识引用可形成证据，任务十二按上限保守校验，后续编排只展示或修正；本类型不负责查询价格、验证白名单或汇总预算。
 *
 * @param status 已知或缺失状态，不得为 {@code null}
 * @param minimum 已知费用下限；缺失费用必须为 {@code null}
 * @param maximum 已知费用上限；缺失费用必须为 {@code null}
 * @param evidenceType 已知费用的证据类型；缺失费用必须为 {@code null}
 * @param knowledgeChunkId DOCUMENT 费用对应的正切片编号；其他来源必须为 {@code null}
 */
public record CostEstimate(
        CostStatus status,
        BigDecimal minimum,
        BigDecimal maximum,
        CostEvidenceType evidenceType,
        Long knowledgeChunkId) {

    static final Comparator<CostEstimate> DETERMINISTIC_ORDER = Comparator
            .comparing(CostEstimate::status)
            .thenComparing(CostEstimate::minimum, Comparator.nullsFirst(BigDecimal::compareTo))
            .thenComparingInt(cost -> cost.minimum() == null ? -1 : cost.minimum().scale())
            .thenComparing(CostEstimate::maximum, Comparator.nullsFirst(BigDecimal::compareTo))
            .thenComparingInt(cost -> cost.maximum() == null ? -1 : cost.maximum().scale())
            .thenComparing(CostEstimate::evidenceType, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(CostEstimate::knowledgeChunkId, Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * 创建不可变费用。
     * KNOWN 要求完整、非负且下限不大于上限；DOCUMENT 必须提供正 chunkId，其他来源禁止携带 chunkId；MISSING 禁止携带金额或证据。
     * 构造只校验内存数据，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当状态为空，或 KNOWN 的金额/来源为空
     * @throws IllegalArgumentException 当金额、区间、来源与引用组合不符合上述前置条件
     */
    public CostEstimate {
        Objects.requireNonNull(status, "status");
        if (status == CostStatus.MISSING) {
            if (minimum != null || maximum != null || evidenceType != null || knowledgeChunkId != null) {
                throw new IllegalArgumentException("missing cost cannot contain evidence");
            }
        } else {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(evidenceType, "evidenceType");
            if (minimum.signum() < 0 || maximum.signum() < 0 || minimum.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("invalid cost range");
            }
            if (evidenceType == CostEvidenceType.DOCUMENT) {
                if (knowledgeChunkId == null || knowledgeChunkId <= 0) {
                    throw new IllegalArgumentException("document cost requires chunkId");
                }
            } else if (knowledgeChunkId != null) {
                throw new IllegalArgumentException("only document cost can contain chunkId");
            }
        }
    }

    /**
     * 创建带证据的已知费用区间。
     *
     * @param minimum 非空且非负的下限
     * @param maximum 非空且不小于下限的上限
     * @param evidenceType 非空证据类型
     * @param knowledgeChunkId DOCUMENT 时为正数，其他类型必须为空
     * @return 不可变 KNOWN 费用
     * @throws NullPointerException 当必要参数为空
     * @throws IllegalArgumentException 当区间或证据组合非法
     * 本方法只创建内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static CostEstimate known(
            BigDecimal minimum, BigDecimal maximum, CostEvidenceType evidenceType, Long knowledgeChunkId) {
        return new CostEstimate(CostStatus.KNOWN, minimum, maximum, evidenceType, knowledgeChunkId);
    }

    /**
     * 创建明确缺失的费用。
     *
     * @return 不含金额或证据的不可变 MISSING 费用
     * 无输入、无空值语义且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static CostEstimate missing() {
        return new CostEstimate(CostStatus.MISSING, null, null, null, null);
    }

    /**
     * 判断费用是否包含可累计区间。
     *
     * @return KNOWN 时为 {@code true}，MISSING 时为 {@code false}
     * 无输入且不抛业务异常；本方法只读取内存状态，不访问外部资源，不处理认证。
     */
    public boolean isKnown() {
        return status == CostStatus.KNOWN;
    }
}
