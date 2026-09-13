package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.admin.KnowledgeAdministrationPersistence;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** MyBatis 管理端适配器；远程 Embedding 和磁盘操作始终位于本类事务之外。 */
@Repository
public class MyBatisKnowledgeAdministrationPersistence
        implements KnowledgeAdministrationPersistence {
    private final KnowledgeAdministrationMapper administrationMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TransactionTemplate transactions;

    public MyBatisKnowledgeAdministrationPersistence(
            KnowledgeAdministrationMapper administrationMapper,
            KnowledgeChunkMapper chunkMapper,
            TransactionTemplate transactions) {
        this.administrationMapper =
                Objects.requireNonNull(administrationMapper, "administrationMapper");
        this.chunkMapper = Objects.requireNonNull(chunkMapper, "chunkMapper");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public List<DocumentRecord> findAllDocuments() {
        return administrationMapper.findAllDocuments().stream()
                .map(KnowledgeAdministrationRow::toRecord)
                .toList();
    }

    @Override
    public Optional<DocumentRecord> findDocument(Long id) {
        return Optional.ofNullable(administrationMapper.findDocument(id))
                .map(KnowledgeAdministrationRow::toRecord);
    }

    @Override
    public ChunkSlice findChunks(Long documentId, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        long total = administrationMapper.countChunks(documentId);
        List<KnowledgeChunk> items = administrationMapper
                .findChunks(documentId, offset, size).stream()
                .map(KnowledgeChunkRow::toDomain)
                .toList();
        return new ChunkSlice(items, total);
    }

    @Override
    public boolean updateStatus(
            Long id, KnowledgeDocumentStatus expected,
            KnowledgeDocumentStatus target, Instant updatedAt) {
        Integer affected = transactions.execute(status -> administrationMapper.updateStatus(
                id, expected.name(), target.name(), updatedAt));
        return affected != null && affected == 1;
    }

    @Override
    public boolean deleteDocument(Long id, KnowledgeDocumentStatus expected) {
        Integer affected = transactions.execute(status ->
                administrationMapper.deleteDocument(id, expected.name()));
        return affected != null && affected == 1;
    }

    @Override
    public ActiveSnapshot loadActiveSnapshot() {
        return Objects.requireNonNull(transactions.execute(status ->
                new ActiveSnapshot(
                        administrationMapper.countActiveDocuments(),
                        chunkMapper.findAllActive().stream()
                                .map(KnowledgeChunkRow::toDomain)
                                .toList())),
                "active knowledge snapshot transaction returned null");
    }
}
