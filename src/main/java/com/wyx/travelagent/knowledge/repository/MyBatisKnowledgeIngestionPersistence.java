package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 通过 MyBatis 和短事务实现知识入库持久化端口。
 * <p>
 * 输入输出均是不可变领域模型，内部 Row 只为生成键服务。远程 Embedding 和磁盘索引调用位于该适配器
 * 之外；这里只有单行状态迁移或一个切片批次事务，也不向 Controller 暴露 Row 或实现任务十检索。
 */
@Repository
public class MyBatisKnowledgeIngestionPersistence implements KnowledgeIngestionPersistence {
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建 MyBatis 持久化适配器。
     *
     * @param documentMapper 文档 SQL 映射
     * @param chunkMapper 切片 SQL 映射
     * @param transactionTemplate Spring 短事务模板
     */
    public MyBatisKnowledgeIngestionPersistence(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            TransactionTemplate transactionTemplate) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper");
        this.chunkMapper = Objects.requireNonNull(chunkMapper, "chunkMapper");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    @Override
    public Optional<KnowledgeDocument> findBySha256(String sha256) {
        KnowledgeDocumentRow row = documentMapper.findBySha256(sha256);
        return Optional.ofNullable(row).map(KnowledgeDocumentRow::toDomain);
    }

    @Override
    public KnowledgeDocument insertUploaded(KnowledgeDocument document) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                KnowledgeDocumentRow row = KnowledgeDocumentRow.from(document);
                if (documentMapper.insert(row) != 1 || row.getId() == null) {
                    throw new IllegalStateException("knowledge document insert affected an unexpected row count");
                }
                return row.toDomain();
            }), "insert transaction returned null");
        } catch (DuplicateKeyException exception) {
            throw new DuplicateKnowledgeDocumentException(exception);
        }
    }

    @Override
    public boolean transition(
            Long documentId,
            KnowledgeDocumentStatus expected,
            KnowledgeDocumentStatus target) {
        Integer affected = transactionTemplate.execute(status -> documentMapper.transition(
                documentId, expected.name(), target.name(), Instant.now()));
        return affected != null && affected == 1;
    }

    @Override
    public List<KnowledgeChunk> insertAllChunks(List<KnowledgeChunk> chunks) {
        return List.copyOf(Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<KnowledgeChunk> stored = new ArrayList<>(chunks.size());
            for (KnowledgeChunk chunk : chunks) {
                KnowledgeChunkRow row = KnowledgeChunkRow.from(chunk);
                if (chunkMapper.insert(row) != 1 || row.getId() == null) {
                    throw new IllegalStateException("knowledge chunk insert affected an unexpected row count");
                }
                stored.add(row.toDomain());
            }
            return stored;
        }), "chunk transaction returned null"));
    }

    @Override
    public boolean markFailed(Long documentId, String safeReason) {
        Integer affected = transactionTemplate.execute(status ->
                documentMapper.markFailed(documentId, safeReason, Instant.now()));
        return affected != null && affected == 1;
    }

    @Override
    public List<KnowledgeChunk> findAllActiveChunks() {
        return chunkMapper.findAllActive().stream().map(KnowledgeChunkRow::toDomain).toList();
    }

    @Override
    public void deleteChunks(Long documentId) {
        transactionTemplate.executeWithoutResult(status -> chunkMapper.deleteByDocumentId(documentId));
    }
}
