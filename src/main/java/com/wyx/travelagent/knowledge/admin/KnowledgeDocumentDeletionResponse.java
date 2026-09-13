package com.wyx.travelagent.knowledge.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/** 文档删除完成摘要，不包含被清理文件的位置。 */
public record KnowledgeDocumentDeletionResponse(
        Long id, boolean deleted,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant deletedAt
) {
}
