package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 后台管理所需的批量只读查询和短数据库写事务端口。 */
public interface KnowledgeAdministrationPersistence {
    List<DocumentRecord> findAllDocuments();

    Optional<DocumentRecord> findDocument(Long id);

    ChunkSlice findChunks(Long documentId, int page, int size);

    boolean updateStatus(
            Long id, KnowledgeDocumentStatus expected,
            KnowledgeDocumentStatus target, Instant updatedAt);

    boolean deleteDocument(Long id, KnowledgeDocumentStatus expected);

    ActiveSnapshot loadActiveSnapshot();

    record DocumentRecord(KnowledgeDocument document, long chunkCount) {
    }

    record ChunkSlice(List<KnowledgeChunk> items, long totalElements) {
        public ChunkSlice {
            items = List.copyOf(items);
        }
    }

    record ActiveSnapshot(long activeDocumentCount, List<KnowledgeChunk> chunks) {
        public ActiveSnapshot {
            chunks = List.copyOf(chunks);
        }
    }
}
