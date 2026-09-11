package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.metrics.PlanMetrics;

/**
 * 业务问题：以显式 DTO 公开四项客观质量指标，不泄漏内部指标类型。
 * 输入是已计算指标，输出稳定 JSON；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四行程响应；不负责指标计算、持久化、Trace 或任务十五页面。
 * @param sourceCoverage 非空来源覆盖率
 * @param routeCoverage 非空路线覆盖率
 * @param budgetCompleteness 非空预算完整率
 * @param rulePassRate 非空规则通过率
 */
public record PlanMetricsResponse(PlanMetricRatioResponse sourceCoverage,
        PlanMetricRatioResponse routeCoverage, PlanMetricRatioResponse budgetCompleteness,
        PlanMetricRatioResponse rulePassRate) {
    /**
     * @param metrics 非空领域指标
     * @return 非空显式指标 DTO
     * 映射只读内存，不访问外部资源或处理认证。
     */
    public static PlanMetricsResponse from(PlanMetrics metrics) {
        return new PlanMetricsResponse(PlanMetricRatioResponse.from(metrics.sourceCoverage()),
                PlanMetricRatioResponse.from(metrics.routeCoverage()),
                PlanMetricRatioResponse.from(metrics.budgetCompleteness()),
                PlanMetricRatioResponse.from(metrics.rulePassRate()));
    }
}
