package com.wyx.travelagent.knowledge.admin;

import java.util.List;

/** 有界切片分页响应。 */
public record KnowledgeChunkPageResponse(
        List<KnowledgeChunkPreviewResponse> items,
        int page, int size, long totalElements, int totalPages
) {
    public KnowledgeChunkPageResponse {
        items = List.copyOf(items);
    }
}
