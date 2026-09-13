package com.wyx.travelagent.knowledge.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/** 手动重建索引的安全计数摘要。 */
public record KnowledgeReindexResponse(
        long activeDocumentCount, long chunkCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant rebuiltAt
) {
}
