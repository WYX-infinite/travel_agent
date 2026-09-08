package com.wyx.travelagent.knowledge.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定义城市知识检索的固定上限和相关性阈值。
 * @param minScore 允许进入引用白名单的最低分，取闭区间 {@code [0,1]}
 * @param candidateLimit 城市过滤后的候选上限，不得超过 8
 * @param contextLimit 本次引用上限，不得超过 5 或候选上限
 */
@ConfigurationProperties("travel.knowledge.retrieval")
public record KnowledgeRetrievalProperties(double minScore, int candidateLimit, int contextLimit) {
    /** 校验配置，防止运维配置绕过固定安全上限。 */
    public KnowledgeRetrievalProperties {
        if (!Double.isFinite(minScore) || minScore < 0.0 || minScore > 1.0
                || candidateLimit <= 0 || candidateLimit > 8
                || contextLimit <= 0 || contextLimit > 5 || contextLimit > candidateLimit) {
            throw new KnowledgeRetrievalException(
                    "INVALID_KNOWLEDGE_RETRIEVAL_CONFIG", "knowledge retrieval configuration is invalid");
        }
    }
}
