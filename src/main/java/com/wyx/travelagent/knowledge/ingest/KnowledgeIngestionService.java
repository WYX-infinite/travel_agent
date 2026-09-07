package com.wyx.travelagent.knowledge.ingest;

import com.wyx.travelagent.knowledge.admin.UploadedKnowledgeDocument;
import com.wyx.travelagent.knowledge.index.KnowledgeIndexException;
import com.wyx.travelagent.knowledge.index.KnowledgeIngestionIndex;
import com.wyx.travelagent.knowledge.index.PreparedKnowledgeIndexBatch;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.repository.DuplicateKnowledgeDocumentException;
import com.wyx.travelagent.knowledge.repository.KnowledgeIngestionPersistence;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 协调 UPLOADED → INDEXING → ACTIVE 的安全知识入库生命周期。
 * <p>
 * 输入是任务八已经校验、落盘并清理正文的 {@link UploadedKnowledgeDocument}，输出是最终 ACTIVE 文档。
 * 数据库、远程 Embedding 和磁盘文件无法组成一个真正的 ACID 事务，因此本服务使用多个短数据库阶段、
 * 索引原子替换以及 ACTIVE 数据补偿重建；它不重新校验上传文件、不访问 sourceUrl，也不实现任务十检索。
 */
@Service
public final class KnowledgeIngestionService {
    private final KnowledgeIngestionPersistence persistence;
    private final TextChunker chunker;
    private final KnowledgeIngestionIndex index;

    /**
     * 创建入库协调器。
     *
     * @param persistence 只执行短数据库事务的持久化端口
     * @param chunker     任务八确定性正文切片器
     * @param index       支持锁外向量准备和原子发布的索引
     */
    public KnowledgeIngestionService(
            KnowledgeIngestionPersistence persistence,
            TextChunker chunker,
            KnowledgeIngestionIndex index) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * 完成一份任务八安全输出的数据库、Embedding 和索引生命周期。
     * <p>
     * 正常查询提前拒绝重复哈希，数据库唯一约束再封堵并发竞态。远程 Embedding 在任何数据库事务和
     * 索引写锁之外完成；只有全部向量成功后才以短事务写切片。最终 ACTIVE 更新失败时，从数据库
     * ACTIVE 切片全集重建索引，移除尚处于 INDEXING 的本次文档，再将它条件迁移为 FAILED。
     *
     * @param uploaded 任务八产生的 UPLOADED 元数据和清理后正文
     * @return 带 Long 主键且状态为 ACTIVE 的文档快照
     * @throws KnowledgeIngestionException 重复、状态冲突、Embedding、数据库、磁盘或补偿失败时抛出
     */
    public KnowledgeDocument ingest(UploadedKnowledgeDocument uploaded) {
        Objects.requireNonNull(uploaded, "uploaded");
        KnowledgeDocument source = uploaded.document();
        if (persistence.findBySha256(source.sha256()).isPresent()) {
            throw duplicate(null);
        }

        final KnowledgeDocument persisted;
        try {
            persisted = persistence.insertUploaded(source);
        } catch (DuplicateKnowledgeDocumentException exception) {
            // “先查后插”不能消除并发窗口，最终仍以数据库唯一约束作为可靠防线。
            throw duplicate(exception);
        }
        if (!persistence.transition(
                persisted.id(), KnowledgeDocumentStatus.UPLOADED, KnowledgeDocumentStatus.INDEXING)) {
            throw failure(
                    "KNOWLEDGE_DOCUMENT_STATE_CONFLICT",
                    "knowledge document state changed concurrently",
                    HttpStatus.CONFLICT,
                    null);
        }

        try {
            List<KnowledgeChunk> chunks = createChunks(persisted.id(), uploaded.extractedText());
            /*
             * 远程模型响应时间和故障不受 MySQL 控制，绝不能把它放进长数据库事务。
             * prepare 先为全部切片生成向量；任何一片失败都不会执行切片事务或污染候选索引。
             */
            PreparedKnowledgeIndexBatch prepared = index.prepare(chunks);

            List<KnowledgeChunk> storedChunks;
            try {
                storedChunks = persistence.insertAllChunks(chunks);
            } catch (RuntimeException exception) {
                throw failure(
                        "KNOWLEDGE_CHUNK_PERSIST_FAILED",
                        "knowledge chunk persistence failed",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        exception);
            }

            AtomicReference<RuntimeException> activationError = new AtomicReference<>();
            boolean activated = index.addPreparedAndActivate(prepared, storedChunks, () -> {
                try {
                    return persistence.transition(
                            persisted.id(), KnowledgeDocumentStatus.INDEXING, KnowledgeDocumentStatus.ACTIVE);
                } catch (RuntimeException exception) {
                    activationError.set(exception);
                    return false;
                }
            });
            if (!activated) {
                return compensateActivationFailure(persisted, activationError.get());
            }
            return withStatus(persisted, KnowledgeDocumentStatus.ACTIVE, null);
        } catch (KnowledgeIngestionException exception) {
            if ("KNOWLEDGE_DOCUMENT_ACTIVATION_FAILED".equals(exception.code())
                    || "KNOWLEDGE_INGESTION_COMPENSATION_FAILED".equals(exception.code())) {
                throw exception;
            }
            markFailedOrEscalate(persisted.id(), safeFailureReason(exception), exception);
            throw exception;
        } catch (KnowledgeIndexException exception) {
            KnowledgeIngestionException converted = failure(
                    exception.code(), exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, exception);
            markFailedOrEscalate(persisted.id(), safeFailureReason(converted), converted);
            throw converted;
        } catch (RuntimeException exception) {
            KnowledgeIngestionException converted = failure(
                    "KNOWLEDGE_INGESTION_FAILED",
                    "knowledge document ingestion failed",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception);
            markFailedOrEscalate(persisted.id(), "knowledge document ingestion failed", converted);
            throw converted;
        }
    }

    /**
     * 使用数据库当前 ACTIVE 切片全集显式重建正式索引。
     *
     * @throws KnowledgeIndexException 查询向量生成或磁盘原子替换失败时抛出
     */
    public void rebuildFromActiveDocuments() {
        index.rebuild(persistence.findAllActiveChunks());
    }

    private KnowledgeDocument compensateActivationFailure(
            KnowledgeDocument document, RuntimeException activationCause) {
        KnowledgeIngestionException activationFailure = failure(
                "KNOWLEDGE_DOCUMENT_ACTIVATION_FAILED",
                "knowledge document could not be activated",
                HttpStatus.INTERNAL_SERVER_ERROR,
                activationCause);
        RuntimeException rebuildFailure = null;
        try {
            /*
             * 正式索引已经可能包含本次切片，而数据库仍是 INDEXING。立即用数据库 ACTIVE 全集重建，
             * 可把本次文档移出正式文件和内存；进程崩溃时同一规则由启动恢复再次执行。
             */
            index.rebuild(persistence.findAllActiveChunks());
            if (activationCause != null) {
                /*
                 * 数据库提交成功后连接仍可能中断，调用方此时只能看到异常。补偿重建已经以 ACTIVE
                 * 数据为准完成，因此重新读取同一哈希；若确认本行实际为 ACTIVE，就按幂等成功返回，
                 * 不能再尝试用 INDEXING → FAILED 覆盖一个已经提交的合法状态。
                 */
                KnowledgeDocument observed = persistence.findBySha256(document.sha256()).orElse(null);
                if (observed != null
                        && document.id().equals(observed.id())
                        && observed.status() == KnowledgeDocumentStatus.ACTIVE) {
                    return observed;
                }
            }
        } catch (RuntimeException compensationFailure) {
            activationFailure.addSuppressed(compensationFailure);
            rebuildFailure = compensationFailure;
        }
        markFailedOrEscalate(document.id(), "knowledge document activation failed", activationFailure);
        if (rebuildFailure != null) {
            KnowledgeIngestionException observable = failure(
                    "KNOWLEDGE_INGESTION_COMPENSATION_FAILED",
                    "knowledge ingestion failed and index compensation did not complete",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    activationFailure);
            observable.addSuppressed(rebuildFailure);
            throw observable;
        }
        throw activationFailure;
    }

    private List<KnowledgeChunk> createChunks(Long documentId, String text) {
        List<TextChunker.TextChunk> textChunks = chunker.chunk(text);
        if (textChunks.isEmpty()) {
            throw failure(
                    "KNOWLEDGE_DOCUMENT_EMPTY",
                    "knowledge document contains no indexable text",
                    HttpStatus.BAD_REQUEST,
                    null);
        }
        Instant now = Instant.now();
        return textChunks.stream()
                .map(chunk -> new KnowledgeChunk(
                        null,
                        documentId,
                        chunk.index(),
                        chunk.content(),
                        stableEmbeddingId(documentId, chunk.index()),
                        now))
                .toList();
    }

    private String stableEmbeddingId(Long documentId, int chunkIndex) {
        /*
         * ID 只由数据库 documentId 和确定性 chunkIndex 构成，既不泄露正文或原文件名，也不依赖随机数；
         * 因而数据库行、磁盘向量和后续引用能够稳定关联，长度也远低于 schema 的 128 字符限制。
         */
        String value = "knowledge:" + documentId + ":chunk:" + chunkIndex;
        if (value.length() > 128) {
            throw new IllegalArgumentException("stable embedding id exceeds database limit");
        }
        return value;
    }

    private void markFailedOrEscalate(
            Long documentId, String safeReason, KnowledgeIngestionException primary) {
        try {
            if (!persistence.markFailed(documentId, safeReason)) {
                throw new IllegalStateException("conditional FAILED transition affected no row");
            }
        } catch (RuntimeException compensationFailure) {
            KnowledgeIngestionException observable = failure(
                    "KNOWLEDGE_INGESTION_COMPENSATION_FAILED",
                    "knowledge ingestion failed and database compensation did not complete",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    primary);
            observable.addSuppressed(compensationFailure);
            throw observable;
        }
    }

    private String safeFailureReason(KnowledgeIngestionException exception) {
        return switch (exception.code()) {
            case "KNOWLEDGE_EMBEDDING_FAILED" -> "knowledge embedding generation failed";
            case "KNOWLEDGE_CHUNK_PERSIST_FAILED" -> "knowledge chunk persistence failed";
            case "KNOWLEDGE_INDEX_PERSIST_FAILED", "KNOWLEDGE_INDEX_SERIALIZATION_FAILED" ->
                    "knowledge index persistence failed";
            default -> "knowledge document ingestion failed";
        };
    }

    private KnowledgeIngestionException duplicate(Throwable cause) {
        return failure(
                "DUPLICATE_KNOWLEDGE_DOCUMENT",
                "knowledge document content already exists",
                HttpStatus.CONFLICT,
                cause);
    }

    private KnowledgeIngestionException failure(
            String code, String message, HttpStatus status, Throwable cause) {
        return new KnowledgeIngestionException(code, message, status, cause);
    }

    private KnowledgeDocument withStatus(
            KnowledgeDocument source, KnowledgeDocumentStatus status, String failureReason) {
        return new KnowledgeDocument(
                source.id(), source.title(), source.city(), source.publisher(), source.sourceUrl(),
                source.publishedAt(), source.documentType(), source.originalFilename(), source.storedFilename(),
                source.contentType(), source.fileSize(), source.sha256(), status, failureReason,
                source.uploadedBy(), source.createdAt(), Instant.now());
    }
}
