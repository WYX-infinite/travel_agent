package com.wyx.travelagent.knowledge.retrieval;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 纯内存校验模型提交的切片 ID，禁止引用本次检索 Top 5 白名单之外的任何数据。
 */
public final class CitationValidator {
    private static final CitationIssue INVALID_CITATION = new CitationIssue(
            "INVALID_CITATION", "one or more citations are not allowed for this response");

    /**
     * @param requestedChunkIds 模型为当前回答提交的切片 ID，可为 null
     * @param allowedReferences 当前请求检索产生的引用白名单
     * @return 只含当前白名单对象的不可变校验结果
     */
    public CitationValidationResult validate(
            List<Long> requestedChunkIds, List<KnowledgeReference> allowedReferences) {
        List<Long> requested = requestedChunkIds == null ? List.of() : requestedChunkIds;
        List<KnowledgeReference> allowed = allowedReferences == null ? List.of() : allowedReferences;
        LinkedHashMap<Long, KnowledgeReference> allowedById = new LinkedHashMap<>();
        allowed.stream().filter(Objects::nonNull)
                .forEach(reference -> allowedById.putIfAbsent(reference.chunkId(), reference));
        Set<Long> allowedIds = Set.copyOf(allowedById.keySet());
        Set<Long> requestedValid = new LinkedHashSet<>();
        Set<Long> rejected = new LinkedHashSet<>();
        int invalidValues = 0;
        for (Long id : requested) {
            if (id == null || id <= 0) {
                invalidValues++;
            } else if (allowedIds.contains(id)) {
                requestedValid.add(id);
            } else {
                rejected.add(id);
            }
        }

        /* 以服务端检索顺序为准，绝不让模型提交顺序重排或替换可信引用对象。 */
        List<KnowledgeReference> valid = allowedById.values().stream()
                .filter(reference -> requestedValid.contains(reference.chunkId()))
                .toList();
        boolean invalid = invalidValues > 0 || !rejected.isEmpty();
        return new CitationValidationResult(valid, List.copyOf(rejected), invalidValues,
                invalid ? List.of(INVALID_CITATION) : List.of());
    }
}
