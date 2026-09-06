package com.wyx.travelagent.knowledge.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示一个已经归属到文档的不可变知识切片。
 * <p>
 * 它接收所属文档 ID、从零开始的稳定序号和正文，并为任务九预留 {@code embeddingId} 标识边界。
 * 任务八只定义模型和确定性切片规则，不生成 Embedding ID、不写数据库，也不建立向量索引。
 *
 * @param id          数据库切片主键；尚未持久化时可以为空
 * @param documentId  所属知识文档的数据库 ID
 * @param chunkIndex  从 0 开始且在同一文档内稳定的切片序号
 * @param content     切片正文
 * @param embeddingId 任务九写入 EmbeddingStore 后使用的标识；任务八保持为空
 * @param createdAt   切片记录创建时间
 */
public record KnowledgeChunk(
        Long id,
        Long documentId,
        int chunkIndex,
        String content,
        String embeddingId,
        Instant createdAt
) {
    /**
     * 校验文档归属、稳定序号和正文等基本不变量。
     *
     * @throws NullPointerException     必填引用字段为空时抛出
     * @throws IllegalArgumentException 文档 ID 非正数、序号为负或正文为空时抛出
     */
    public KnowledgeChunk {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
