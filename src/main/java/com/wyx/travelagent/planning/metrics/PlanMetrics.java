package com.wyx.travelagent.planning.metrics;

import java.util.Objects;

/**
 * 业务问题：冻结一个行程版本的四项客观质量指标，供版本比较与 Trace 审计。
 * 输入是已经计算好的可审计比率，输出为不可变指标快照；本类型不访问外部资源或处理认证。
 * 任务十三在每个可解析版本完成全量 Java 校验后创建本对象；本类型不负责模型判断或数据库写入。
 *
 * @param sourceCoverage 具有 FACT 或有效 DOCUMENT 证据的条目数/全部条目数
 * @param routeCoverage 具有已验证路线事实的相邻移动数/全部相邻移动数
 * @param budgetCompleteness 具有已知费用的条目数/全部条目数
 * @param rulePassRate 未产生问题的规则数/实际执行规则数
 */
public record PlanMetrics(
        MetricRatio sourceCoverage,
        MetricRatio routeCoverage,
        MetricRatio budgetCompleteness,
        MetricRatio rulePassRate) {

    /** 创建非空指标快照；只校验内存对象，不访问外部资源，也不处理认证。 */
    public PlanMetrics {
        Objects.requireNonNull(sourceCoverage, "sourceCoverage");
        Objects.requireNonNull(routeCoverage, "routeCoverage");
        Objects.requireNonNull(budgetCompleteness, "budgetCompleteness");
        Objects.requireNonNull(rulePassRate, "rulePassRate");
    }
}
