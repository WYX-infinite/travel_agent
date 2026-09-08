package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalMetadata;

import java.net.URI;
import java.time.LocalDate;

/**
 * MyBatis 城市检索联表查询使用的可变行对象，仅在仓储边界内存在。
 */
final class KnowledgeRetrievalRow {
    private Long documentId;
    private Long chunkId;
    private int chunkIndex;
    private String embeddingId;
    private String content;
    private String title;
    private String city;
    private String publisher;
    private String sourceUrl;
    private LocalDate publishedAt;
    private String documentType;

    /** @return 转换后的不可变检索元数据；URL 仅解析，不发起网络访问 */
    KnowledgeRetrievalMetadata toDomain() {
        return new KnowledgeRetrievalMetadata(documentId, chunkId, chunkIndex, embeddingId, content,
                title, city, publisher, URI.create(sourceUrl), publishedAt,
                KnowledgeDocumentType.valueOf(documentType));
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getEmbeddingId() { return embeddingId; }
    public void setEmbeddingId(String embeddingId) { this.embeddingId = embeddingId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDate getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
}
