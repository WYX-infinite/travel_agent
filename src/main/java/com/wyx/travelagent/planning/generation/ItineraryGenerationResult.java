package com.wyx.travelagent.planning.generation;

import com.wyx.travelagent.planning.model.ItineraryPlan;

import java.util.Objects;

/**
 * 业务问题：把一次模型调用的可解析行程、实际 Token 元数据和引用净化计数作为单一结果传给编排器。
 * 输入不保存完整 Prompt 或模型原文，输出为不可变安全摘要；本类型不访问外部资源或处理认证。
 * 任务十三用它区分实际 Token 与估算值；本类型不负责重试、全量校验、修正或持久化。
 *
 * @param plan 严格解析且 POI 白名单通过的行程
 * @param tokenUsage 模型实际用量或明确未知值
 * @param invalidReferenceCount 被安全降级为 MISSING 的越界 DOCUMENT 引用数
 */
public record ItineraryGenerationResult(
        ItineraryPlan plan,
        ModelTokenUsage tokenUsage,
        int invalidReferenceCount) {

    /** 创建安全生成结果；只校验内存值，不访问外部资源，也不处理认证。 */
    public ItineraryGenerationResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(tokenUsage, "tokenUsage");
        if (invalidReferenceCount < 0) throw new IllegalArgumentException("invalidReferenceCount");
    }
}
