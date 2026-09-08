package com.wyx.travelagent.knowledge.retrieval;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 数据库一次批量查询返回的 ACTIVE 城市切片及可信来源元数据。
 *
 * @param documentId 文档主键
 * @param chunkId 切片主键
 * @param chunkIndex 切片序号
 * @param embeddingId 向量稳定标识
 * @param content 数据库正文
 * @param title 来源标题
 * @param city 精确城市名
 * @param publisher 发布者
 * @param sourceUrl HTTP(S) 来源地址
 * @param publishedAt 发布日期
 * @param documentType 文档类型
 */
public record KnowledgeRetrievalMetadata(
        Long documentId, Long chunkId, int chunkIndex, String embeddingId, String content,
        String title, String city, String publisher, URI sourceUrl, LocalDate publishedAt,
        KnowledgeDocumentType documentType) {
    /** 校验数据库映射后的领域不变量。 */
    public KnowledgeRetrievalMetadata {
        if (documentId == null || documentId <= 0 || chunkId == null || chunkId <= 0 || chunkIndex < 0) {
            throw new IllegalArgumentException("knowledge retrieval identifiers are invalid");
        }
        embeddingId = requireText(embeddingId, "embeddingId");
        String expectedEmbeddingId = "knowledge:" + documentId + ":chunk:" + chunkIndex;
        if (!expectedEmbeddingId.equals(embeddingId) || embeddingId.length() > 128) {
            throw new IllegalArgumentException("embeddingId does not match database identifiers");
        }
        content = requireText(content, "content");
        title = requireText(title, "title");
        city = requireText(city, "city");
        publisher = requireText(publisher, "publisher");
        requireHttpUrl(sourceUrl);
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(documentType, "documentType");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static void requireHttpUrl(URI value) {
        Objects.requireNonNull(value, "sourceUrl");
        String scheme = value.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || value.getHost() == null || value.getUserInfo() != null) {
            throw new IllegalArgumentException("sourceUrl must be an absolute HTTP(S) URL");
        }
    }
}
