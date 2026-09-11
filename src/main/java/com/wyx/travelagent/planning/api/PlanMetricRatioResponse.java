package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.metrics.MetricRatio;

/**
 * 业务问题：公开可复核的指标分子、分母与可选百分比，保留零分母“不适用”语义。
 * 输入是领域比率，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四指标响应；不负责选择统计口径、重新计算或前端展示。
 * @param numerator 非负满足数量
 * @param denominator 非负总数量；零表示不适用
 * @param percentage 可为空的 0 至 100 百分比
 */
public record PlanMetricRatioResponse(int numerator, int denominator, Double percentage) {
    /**
     * @param ratio 非空领域比率
     * @return 非空 DTO；零分母时 percentage 为 null
     * 本方法只读内存，不访问外部资源或处理认证。
     */
    public static PlanMetricRatioResponse from(MetricRatio ratio) {
        return new PlanMetricRatioResponse(ratio.numerator(), ratio.denominator(),
                ratio.percentage().orElse(null));
    }
}
