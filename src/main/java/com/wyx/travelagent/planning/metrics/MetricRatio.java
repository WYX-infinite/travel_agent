package com.wyx.travelagent.planning.metrics;

import java.util.Optional;

/**
 * 业务问题：用分子和分母保存可审计指标，避免持久化后只剩无法复核的百分比。
 * 输入是非负计数，输出为不可变比率；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三的 Trace 和版本指标共同使用本类型；本类型不负责选择统计口径或把不适用误写成零分。
 *
 * @param numerator 满足条件的数量，不得为负且不得大于分母
 * @param denominator 总数量；零表示该指标不适用
 */
public record MetricRatio(int numerator, int denominator) {

    /**
     * 创建可复核比率。
     *
     * @throws IllegalArgumentException 当计数为负或分子大于分母
     * 构造只校验内存计数，不访问任何外部资源，也不处理认证。
     */
    public MetricRatio {
        if (numerator < 0 || denominator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("INVALID_METRIC_RATIO");
        }
    }

    /**
     * 判断该指标是否有可计算的样本。
     *
     * @return 分母大于零时为 {@code true}
     * 本方法只读取内存计数，不访问外部资源，也不处理认证。
     */
    public boolean applicable() {
        return denominator > 0;
    }

    /**
     * 计算百分比，同时保留零分母的“不适用”语义。
     *
     * @return 有样本时为 0 至 100 的百分比，否则为空
     * 本方法不把零分母伪造成 0% 或 100%，也不访问外部资源，不处理认证。
     */
    public Optional<Double> percentage() {
        return applicable() ? Optional.of(numerator * 100.0 / denominator) : Optional.empty();
    }
}
