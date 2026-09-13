package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.index.KnowledgeIndex;
import com.wyx.travelagent.knowledge.index.KnowledgeIndexException;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** 查询并协调知识文档启停、删除和索引重建的独立后台管理边界。 */
@Service
public class KnowledgeAdministrationService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(KnowledgeAdministrationService.class);
    private static final int MAX_CHUNK_PAGE_SIZE = 100;

    private final KnowledgeAdministrationPersistence persistence;
    private final KnowledgeIndex index;
    private final Path uploadRoot;
    private final Clock clock;
    private final KnowledgeAdministrationFileOperations files;
    private final ReentrantLock writeLock = new ReentrantLock(true);

    /** 生产构造器；上传根目录来自已验证的知识上传配置。 */
    @Autowired
    public KnowledgeAdministrationService(
            KnowledgeAdministrationPersistence persistence,
            KnowledgeIndex index,
            KnowledgeUploadProperties properties) {
        this(persistence, index, properties, Clock.systemUTC(),
                new NioKnowledgeAdministrationFileOperations());
    }

    KnowledgeAdministrationService(
            KnowledgeAdministrationPersistence persistence,
            KnowledgeIndex index,
            KnowledgeUploadProperties properties,
            Clock clock,
            KnowledgeAdministrationFileOperations files) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.index = Objects.requireNonNull(index, "index");
        this.uploadRoot = Objects.requireNonNull(properties, "properties")
                .uploadRoot().toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** 一次聚合查询返回稳定排序的安全列表 DTO。 */
    public List<KnowledgeDocumentSummaryResponse> listDocuments() {
        try {
            return persistence.findAllDocuments().stream().map(this::summary).toList();
        } catch (KnowledgeAdministrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("KNOWLEDGE_ADMINISTRATION_FAILED",
                    "knowledge documents could not be loaded",
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    /** 返回单个安全详情，隐藏 storedFilename 等服务端内部字段。 */
    public KnowledgeDocumentDetailResponse getDocument(Long id) {
        return detail(requiredDocument(id));
    }

    /** 返回 page/size 有界、按切片序号稳定排序的预览页。 */
    public KnowledgeChunkPageResponse getChunks(Long id, int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_CHUNK_PAGE_SIZE) {
            throw failure("INVALID_KNOWLEDGE_CHUNK_PAGE",
                    "chunk page parameters are invalid", HttpStatus.BAD_REQUEST, null);
        }
        requiredDocument(id);
        try {
            KnowledgeAdministrationPersistence.ChunkSlice slice =
                    persistence.findChunks(id, page, size);
            long totalPages = slice.totalElements() == 0
                    ? 0 : ((slice.totalElements() - 1) / size) + 1;
            if (totalPages > Integer.MAX_VALUE) {
                throw new IllegalStateException("knowledge chunk page count exceeds supported range");
            }
            List<KnowledgeChunkPreviewResponse> items = slice.items().stream()
                    .map(this::chunkPreview)
                    .toList();
            return new KnowledgeChunkPageResponse(
                    items, page, size, slice.totalElements(), (int) totalPages);
        } catch (KnowledgeAdministrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("KNOWLEDGE_ADMINISTRATION_FAILED",
                    "knowledge document chunks could not be loaded",
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    /** 条件迁移 ACTIVE/DISABLED，并在失败时恢复数据库状态和索引。 */
    public KnowledgeDocumentDetailResponse changeStatus(Long id, String requestedStatus) {
        KnowledgeDocumentStatus target = parseTargetStatus(requestedStatus);
        writeLock.lock();
        try {
            KnowledgeAdministrationPersistence.DocumentRecord before = requiredDocument(id);
            KnowledgeDocumentStatus original = before.document().status();
            if (original == target) {
                rebuildOrFail();
                return detail(before);
            }
            if (!isManageable(original)) {
                throw stateConflict();
            }
            final boolean updated;
            try {
                updated = persistence.updateStatus(id, original, target, clock.instant());
            } catch (RuntimeException exception) {
                throw failure("KNOWLEDGE_DOCUMENT_STATE_CONFLICT",
                        "knowledge document state changed concurrently",
                        HttpStatus.CONFLICT, exception);
            }
            if (!updated) {
                throw stateConflict();
            }

            try {
                rebuildCurrentActive();
            } catch (RuntimeException rebuildFailure) {
                compensateStatus(id, target, original, rebuildFailure);
            }
            return detail(requiredDocument(id));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 先按 ACTIVE 全集修复索引，再把受控单文件原子移动到 uploadRoot/.deleting，
     * 最后以短事务条件删除数据库行。
     * 数据库失败会原子移回；提交后的物理清理失败只留下可审计隔离文件，不伪装成数据库回滚。
     */
    public KnowledgeDocumentDeletionResponse deleteDocument(Long id) {
        writeLock.lock();
        try {
            KnowledgeDocument document = requiredDocument(id).document();
            if (document.status() != KnowledgeDocumentStatus.DISABLED
                    && document.status() != KnowledgeDocumentStatus.FAILED) {
                throw stateConflict();
            }
            rebuildOrFail();
            QuarantinedFile quarantined = quarantine(document);
            final boolean deleted;
            try {
                deleted = persistence.deleteDocument(id, document.status());
            } catch (RuntimeException exception) {
                restoreOrEscalate(quarantined, exception);
                throw failure("KNOWLEDGE_DOCUMENT_DELETE_FAILED",
                        "knowledge document could not be deleted",
                        HttpStatus.INTERNAL_SERVER_ERROR, exception);
            }
            if (!deleted) {
                KnowledgeAdministrationException conflict = stateConflict();
                restoreOrEscalate(quarantined, conflict);
                throw conflict;
            }
            cleanupAfterCommittedDelete(quarantined, id);
            return new KnowledgeDocumentDeletionResponse(id, true, clock.instant());
        } finally {
            writeLock.unlock();
        }
    }

    /** 使用一个短数据库快照中的全部 ACTIVE 切片重建索引。 */
    public KnowledgeReindexResponse reindex() {
        writeLock.lock();
        try {
            KnowledgeAdministrationPersistence.ActiveSnapshot active = loadActiveSnapshot();
            try {
                index.rebuild(active.chunks());
            } catch (RuntimeException exception) {
                throw rebuildFailure(exception);
            }
            return new KnowledgeReindexResponse(
                    active.activeDocumentCount(), active.chunks().size(), clock.instant());
        } finally {
            writeLock.unlock();
        }
    }

    private void compensateStatus(
            Long id,
            KnowledgeDocumentStatus changed,
            KnowledgeDocumentStatus original,
            RuntimeException rebuildFailure) {
        try {
            if (!persistence.updateStatus(id, changed, original, clock.instant())) {
                throw new IllegalStateException("conditional status restore affected no row");
            }
            rebuildCurrentActive();
        } catch (RuntimeException compensationFailure) {
            KnowledgeAdministrationException observable = failure(
                    "KNOWLEDGE_ADMIN_COMPENSATION_FAILED",
                    "knowledge administration compensation did not complete",
                    HttpStatus.INTERNAL_SERVER_ERROR, rebuildFailure);
            observable.addSuppressed(compensationFailure);
            throw observable;
        }
        throw rebuildFailure(rebuildFailure);
    }

    private void rebuildCurrentActive() {
        index.rebuild(loadActiveSnapshot().chunks());
    }

    private void rebuildOrFail() {
        try {
            rebuildCurrentActive();
        } catch (RuntimeException exception) {
            throw rebuildFailure(exception);
        }
    }

    private KnowledgeAdministrationPersistence.ActiveSnapshot loadActiveSnapshot() {
        try {
            return persistence.loadActiveSnapshot();
        } catch (RuntimeException exception) {
            throw failure("KNOWLEDGE_INDEX_REBUILD_FAILED",
                    "knowledge index source could not be loaded",
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private QuarantinedFile quarantine(KnowledgeDocument document) {
        Path source = controlledStoredFile(document.storedFilename());
        if (!files.exists(source)) {
            return QuarantinedFile.none();
        }
        if (!files.isRegularFile(source)) {
            throw storageInvalid(null);
        }
        Path quarantineRoot = uploadRoot.resolve(".deleting").normalize();
        if (!quarantineRoot.startsWith(uploadRoot)
                || !uploadRoot.equals(quarantineRoot.getParent())) {
            throw storageInvalid(null);
        }
        Path quarantined = quarantineRoot.resolve(UUID.randomUUID() + ".deleting").normalize();
        if (!quarantined.startsWith(quarantineRoot)
                || !quarantineRoot.equals(quarantined.getParent())) {
            throw storageInvalid(null);
        }
        try {
            files.createDirectories(quarantineRoot);
            files.moveAtomically(source, quarantined);
            return new QuarantinedFile(source, quarantined, true);
        } catch (IOException | RuntimeException exception) {
            throw failure("KNOWLEDGE_DOCUMENT_FILE_ISOLATION_FAILED",
                    "knowledge document file could not be isolated",
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private Path controlledStoredFile(String storedFilename) {
        try {
            if (storedFilename == null || storedFilename.isBlank()) {
                throw storageInvalid(null);
            }
            Path relative = Path.of(storedFilename);
            if (relative.isAbsolute() || relative.getNameCount() != 1
                    || ".".equals(storedFilename) || "..".equals(storedFilename)
                    || storedFilename.contains("/") || storedFilename.contains("\\")) {
                throw storageInvalid(null);
            }
            Path resolved = uploadRoot.resolve(relative).normalize();
            if (!resolved.startsWith(uploadRoot) || !uploadRoot.equals(resolved.getParent())) {
                throw storageInvalid(null);
            }
            return resolved;
        } catch (KnowledgeAdministrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw storageInvalid(exception);
        }
    }

    private void restoreOrEscalate(QuarantinedFile quarantined, RuntimeException primary) {
        if (!quarantined.moved()) {
            return;
        }
        try {
            files.moveAtomically(quarantined.quarantined(), quarantined.original());
        } catch (IOException | RuntimeException compensationFailure) {
            KnowledgeAdministrationException observable = failure(
                    "KNOWLEDGE_ADMIN_COMPENSATION_FAILED",
                    "knowledge administration compensation did not complete",
                    HttpStatus.INTERNAL_SERVER_ERROR, primary);
            observable.addSuppressed(compensationFailure);
            throw observable;
        }
    }

    private void cleanupAfterCommittedDelete(QuarantinedFile quarantined, Long id) {
        if (!quarantined.moved()) {
            return;
        }
        try {
            files.deleteIfExists(quarantined.quarantined());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Quarantined knowledge file cleanup remains pending for document {}", id);
        }
    }

    private KnowledgeAdministrationPersistence.DocumentRecord requiredDocument(Long id) {
        if (id == null || id <= 0) {
            throw notFound();
        }
        try {
            return persistence.findDocument(id).orElseThrow(this::notFound);
        } catch (KnowledgeAdministrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("KNOWLEDGE_ADMINISTRATION_FAILED",
                    "knowledge document could not be loaded",
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private KnowledgeDocumentStatus parseTargetStatus(String value) {
        if (value == null) {
            throw invalidStatus();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized)) {
            return KnowledgeDocumentStatus.ACTIVE;
        }
        if ("DISABLED".equals(normalized)) {
            return KnowledgeDocumentStatus.DISABLED;
        }
        throw invalidStatus();
    }

    private boolean isManageable(KnowledgeDocumentStatus status) {
        return status == KnowledgeDocumentStatus.ACTIVE
                || status == KnowledgeDocumentStatus.DISABLED;
    }

    private KnowledgeDocumentSummaryResponse summary(
            KnowledgeAdministrationPersistence.DocumentRecord record) {
        KnowledgeDocument document = record.document();
        return new KnowledgeDocumentSummaryResponse(
                document.id(), document.title(), document.city(), document.publisher(),
                document.sourceUrl(), document.publishedAt(), document.documentType(),
                document.status(), record.chunkCount(), document.createdAt(),
                document.updatedAt(), document.failureReason());
    }

    private KnowledgeDocumentDetailResponse detail(
            KnowledgeAdministrationPersistence.DocumentRecord record) {
        KnowledgeDocument document = record.document();
        return new KnowledgeDocumentDetailResponse(
                document.id(), document.title(), document.city(), document.publisher(),
                document.sourceUrl(), document.publishedAt(), document.documentType(),
                document.status(), record.chunkCount(), document.createdAt(),
                document.updatedAt(), document.failureReason(), document.originalFilename(),
                document.contentType(), document.fileSize(), document.sha256());
    }

    private KnowledgeChunkPreviewResponse chunkPreview(KnowledgeChunk chunk) {
        return new KnowledgeChunkPreviewResponse(
                chunk.id(), chunk.documentId(), chunk.chunkIndex(),
                chunk.content(), chunk.createdAt());
    }

    private KnowledgeAdministrationException rebuildFailure(RuntimeException cause) {
        boolean concurrent = cause instanceof KnowledgeIndexException exception
                && "KNOWLEDGE_INDEX_CONCURRENT_UPDATE".equals(exception.code());
        return failure("KNOWLEDGE_INDEX_REBUILD_FAILED",
                concurrent
                        ? "knowledge index changed concurrently; retry the request"
                        : "knowledge index could not be rebuilt",
                concurrent ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR,
                cause);
    }

    private KnowledgeAdministrationException invalidStatus() {
        return failure("INVALID_KNOWLEDGE_DOCUMENT_STATUS",
                "status must be ACTIVE or DISABLED", HttpStatus.BAD_REQUEST, null);
    }

    private KnowledgeAdministrationException notFound() {
        return failure("KNOWLEDGE_DOCUMENT_NOT_FOUND",
                "knowledge document was not found", HttpStatus.NOT_FOUND, null);
    }

    private KnowledgeAdministrationException stateConflict() {
        return failure("KNOWLEDGE_DOCUMENT_STATE_CONFLICT",
                "knowledge document state does not allow this operation",
                HttpStatus.CONFLICT, null);
    }

    private KnowledgeAdministrationException storageInvalid(Throwable cause) {
        return failure("KNOWLEDGE_DOCUMENT_STORAGE_INVALID",
                "knowledge document storage metadata is invalid",
                HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    private KnowledgeAdministrationException failure(
            String code, String message, HttpStatus status, Throwable cause) {
        return new KnowledgeAdministrationException(code, message, status, cause);
    }

    private record QuarantinedFile(Path original, Path quarantined, boolean moved) {
        static QuarantinedFile none() {
            return new QuarantinedFile(null, null, false);
        }
    }
}
