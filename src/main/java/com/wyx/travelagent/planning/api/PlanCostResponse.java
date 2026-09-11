package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 业务问题：按任务十四冻结协议公开费用区间、来源类型和估算标记，避免把模型估算展示成实时事实。
 * 输入是已校验领域费用，输出固定的 {@code min/max/evidenceType/estimated} JSON；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四行程活动；不负责实时查价、累计预算、解析知识引用或任务十五页面。
 *
 * @param min 可为空费用下限；费用缺失时为空
 * @param max 可为空费用上限；费用缺失时为空
 * @param evidenceType 可为空稳定公开来源：API_FACT、DOCUMENT 或 AI_ESTIMATE
 * @param estimated 仅当费用来源为 AI_ESTIMATE 时为 {@code true}
 */
public record PlanCostResponse(BigDecimal min, BigDecimal max, String evidenceType, boolean estimated) {
    /**
     * 将内部费用来源映射为稳定公开协议。
     *
     * @param cost 非空且已校验领域费用
     * @return 非空显式费用 DTO；MISSING 保持金额和来源为 null，且不冒充估算值
     * 本方法只读内存，不访问外部资源或处理认证；知识切片详情由独立 evidence 协议承载。
     */
    public static PlanCostResponse from(CostEstimate cost) {
        Objects.requireNonNull(cost, "cost");
        CostEvidenceType source = cost.evidenceType();
        return new PlanCostResponse(cost.minimum(), cost.maximum(), publicEvidenceType(source),
                source == CostEvidenceType.AI_ESTIMATE);
    }

    private static String publicEvidenceType(CostEvidenceType source) {
        if (source == null) return null;
        return switch (source) {
            case FACT -> "API_FACT";
            case DOCUMENT -> "DOCUMENT";
            case AI_ESTIMATE -> "AI_ESTIMATE";
        };
    }
}
