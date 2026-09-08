package com.wyx.travelagent.knowledge.retrieval;

import java.util.List;
import java.util.Objects;

/**
 * 同时保留城市内 Top 8 候选和通过阈值后的 Top 5 引用白名单。
 *
 * @param candidateMatches 阈值前的可审计候选
 * @param allowedReferences 本次允许引用的白名单
 */
public record KnowledgeRetrievalResult(
        List<KnowledgeReference> candidateMatches, List<KnowledgeReference> allowedReferences) {
    /** 创建防御性不可变结果。 */
    public KnowledgeRetrievalResult {
        candidateMatches = List.copyOf(Objects.requireNonNull(candidateMatches, "candidateMatches"));
        allowedReferences = List.copyOf(Objects.requireNonNull(allowedReferences, "allowedReferences"));
        if (candidateMatches.size() > 8 || allowedReferences.size() > 5
                || !candidateMatches.containsAll(allowedReferences)) {
            throw new IllegalArgumentException("knowledge retrieval result exceeds its contract");
        }
    }
}
