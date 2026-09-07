package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;

import java.time.Instant;

/**
 * MyBatis 使用的可变切片行，只在 repository 包内承担数据库字段映射。
 * <p>
 * setter 可变性仅用于结果映射和生成 Long 主键回填；{@link #from(KnowledgeChunk)} 与
 * {@link #toDomain()} 负责和不可变领域模型双向转换。本类型不得泄漏到 Controller 或索引公共端口。
 */
final class KnowledgeChunkRow {
    private Long id;
    private Long documentId;
    private int chunkIndex;
    private String content;
    private String embeddingId;
    private Instant createdAt;

    static KnowledgeChunkRow from(KnowledgeChunk chunk) {
        KnowledgeChunkRow row = new KnowledgeChunkRow();
        row.id = chunk.id();
        row.documentId = chunk.documentId();
        row.chunkIndex = chunk.chunkIndex();
        row.content = chunk.content();
        row.embeddingId = chunk.embeddingId();
        row.createdAt = chunk.createdAt();
        return row;
    }

    KnowledgeChunk toDomain() {
        return new KnowledgeChunk(id, documentId, chunkIndex, content, embeddingId, createdAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbeddingId() { return embeddingId; }
    public void setEmbeddingId(String embeddingId) { this.embeddingId = embeddingId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
