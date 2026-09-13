package com.wyx.travelagent.knowledge.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

/** 管理列表的安全文档摘要，不包含磁盘文件名、正文或索引载荷。 */
public record KnowledgeDocumentSummaryResponse(
        Long id, String title, String city, String publisher, URI sourceUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate publishedAt,
        KnowledgeDocumentType documentType,
        KnowledgeDocumentStatus status, long chunkCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
        String failureReason
) {
}
