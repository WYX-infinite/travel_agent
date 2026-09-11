package com.wyx.travelagent.planning;

import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.validation.ValidationReport;

/**
 * 业务问题：把规划最终状态、最近可解析版本和稳定失败语义作为单一不可变返回值。
 * 输入不包含完整 Prompt、模型原文或认证秘密，输出供任务十四以后持久化任务状态；本类型不访问外部资源，也不处理认证。
 * 任务十三只返回同步编排结果；本类型不负责异步任务状态机、网络响应或持久化。
 *
 * @param status 非空最终状态
 * @param planId 已创建行程主记录时的编号，否则为 {@code null}
 * @param version 最近可解析版本号；没有版本时为零
 * @param plan 最近可解析行程；没有版本时为 {@code null}
 * @param validationReport 最近版本的全量校验报告；没有版本时为 {@code null}
 * @param metrics 最近版本的客观指标；没有版本时为 {@code null}
 * @param failureCode 失败时的稳定代码；成功或需调整时为 {@code null}
 */
public record PlanningOutcome(
        PlanningOutcomeStatus status,
        Long planId,
        int version,
        ItineraryPlan plan,
        ValidationReport validationReport,
        PlanMetrics metrics,
        String failureCode) {

    /** 创建语义一致的结果；只校验内存字段，不访问外部资源，也不处理认证。 */
    public PlanningOutcome {
        if (status == null) throw new NullPointerException("status");
        if (version < 0) throw new IllegalArgumentException("version");
        boolean hasVersion = version > 0;
        if (hasVersion != (plan != null && validationReport != null && metrics != null && planId != null)) {
            throw new IllegalArgumentException("INCONSISTENT_PLANNING_OUTCOME");
        }
        if ((status == PlanningOutcomeStatus.FAILED
                || status == PlanningOutcomeStatus.CONTEXT_BUDGET_EXCEEDED) != (failureCode != null)) {
            throw new IllegalArgumentException("INCONSISTENT_FAILURE_CODE");
        }
    }
}
