package com.wyx.travelagent.knowledge.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

/** 管理详情的安全 DTO；服务端存储文件名和路径始终留在管理服务内部。 */
public record KnowledgeDocumentDetailResponse(
        Long id, String title, String city, String publisher, URI sourceUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate publishedAt,
        KnowledgeDocumentType documentType,
        KnowledgeDocumentStatus status, long chunkCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
        String failureReason,
        String originalFilename, String contentType, long fileSize, String sha256
) {
}
