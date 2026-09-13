package com.wyx.travelagent.knowledge.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/** 单条管理端切片预览；不公开 embeddingId 或向量。 */
public record KnowledgeChunkPreviewResponse(
        Long id, Long documentId, int chunkIndex, String content,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
}
