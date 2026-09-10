package com.wyx.travelagent.planning.model;

import java.util.Comparator;
import java.util.Objects;

/**
 * 业务问题：把可信费用区间与具体 POI 绑定，供 FACT/DOCUMENT 来源验证，避免模型自行升级证据等级。
 * 输入为白名单 poiId 和已知费用，输出为不可变费用事实；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十工具结果或知识引用形成输入，任务十二验证模型费用来源，后续编排消费校验结果；本类型不负责查价、累计预算或修正。
 *
 * @param poiId 非空白白名单 POI ID
 * @param estimate 必须为 KNOWN 且来源为 FACT 或 DOCUMENT 的费用
 */
public record CostFact(String poiId, CostEstimate estimate) {
    static final Comparator<CostFact> DETERMINISTIC_ORDER = Comparator
            .comparing(CostFact::poiId)
            .thenComparing(CostFact::estimate, CostEstimate.DETERMINISTIC_ORDER);

    /**
     * 创建不可变可信费用事实。
     * poiId 不得为空，费用必须已知且不能是 AI_ESTIMATE；DOCUMENT 自身已保证带正 chunkId。
     * 构造只校验内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当 poiId 或费用为空
     * @throws IllegalArgumentException 当 poiId 空白、费用缺失或来源为 AI_ESTIMATE
     */
    public CostFact {
        Objects.requireNonNull(poiId, "poiId");
        poiId = poiId.strip();
        if (poiId.isEmpty()) throw new IllegalArgumentException("poiId");
        Objects.requireNonNull(estimate, "estimate");
        if (!estimate.isKnown() || estimate.evidenceType() == CostEvidenceType.AI_ESTIMATE) {
            throw new IllegalArgumentException("trusted cost fact required");
        }
    }
}
