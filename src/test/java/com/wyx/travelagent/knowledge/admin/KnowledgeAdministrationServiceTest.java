package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.index.KnowledgeIndex;
import com.wyx.travelagent.knowledge.index.KnowledgeIndexException;
import com.wyx.travelagent.knowledge.index.KnowledgeMatch;
import com.wyx.travelagent.knowledge.index.KnowledgeQuery;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private Path uploadRoot;
    private FakePersistence persistence;
    private RecordingIndex index;
    private KnowledgeAdministrationService service;

    @BeforeEach
    void setUp() {
        uploadRoot = temporaryDirectory.resolve("uploads");
        persistence = new FakePersistence();
        index = new RecordingIndex();
        service = service(new NioKnowledgeAdministrationFileOperations());
    }

    @Test
    void activeCanBeDisabledAndIndexIsRebuiltFromCurrentDatabaseWhitelist() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 1);
        persistence.chunks.add(chunk(11L, 1L, 0));

        KnowledgeDocumentDetailResponse response = service.changeStatus(1L, "DISABLED");

        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.DISABLED);
        assertThat(persistence.document(1L).status()).isEqualTo(KnowledgeDocumentStatus.DISABLED);
        assertThat(index.rebuilds).containsExactly(List.of());
    }

    @Test
    void disabledCanBeActivatedAndIndexContainsOnlyAllCurrentActiveChunks() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 1);
        persistence.seed(document(2L, KnowledgeDocumentStatus.ACTIVE, "other.txt"), 1);
        KnowledgeChunk enabled = chunk(11L, 1L, 0);
        KnowledgeChunk alreadyActive = chunk(21L, 2L, 0);
        persistence.chunks.addAll(List.of(enabled, alreadyActive));

        service.changeStatus(1L, " active ");

        assertThat(index.rebuilds).singleElement()
                .satisfies(chunks -> assertThat(chunks)
                        .containsExactly(enabled, alreadyActive));
    }

    @Test
    void sameTargetStatusIsIdempotentButReconcilesIndexBeforeReturningSuccess() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 0);

        KnowledgeDocumentDetailResponse response = service.changeStatus(1L, "DISABLED");

        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.DISABLED);
        assertThat(persistence.statusUpdates).isZero();
        assertThat(index.rebuilds).containsExactly(List.of());
    }

    @Test
    void retryingSameTargetAfterCompensationFailureRepairsIndexBeforeSuccess() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 1);
        persistence.chunks.add(chunk(11L, 1L, 0));
        index.failuresRemaining = 1;
        persistence.failRestore = true;

        assertCode(() -> service.changeStatus(1L, "DISABLED"),
                "KNOWLEDGE_ADMIN_COMPENSATION_FAILED", 500);
        KnowledgeDocumentDetailResponse response = service.changeStatus(1L, "DISABLED");

        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.DISABLED);
        assertThat(persistence.statusUpdates).isEqualTo(2);
        assertThat(index.rebuilds).containsExactly(List.of(), List.of());
    }

    @Test
    void unknownTargetAndNonManageableSourceStatusesUseStableErrors() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 0);
        assertCode(() -> service.changeStatus(1L, "ARCHIVED"),
                "INVALID_KNOWLEDGE_DOCUMENT_STATUS", 400);

        for (KnowledgeDocumentStatus status : List.of(
                KnowledgeDocumentStatus.UPLOADED,
                KnowledgeDocumentStatus.INDEXING,
                KnowledgeDocumentStatus.FAILED)) {
            persistence.seed(document(2L, status, "safe.txt"), 0);
            assertCode(() -> service.changeStatus(2L, "DISABLED"),
                    "KNOWLEDGE_DOCUMENT_STATE_CONFLICT", 409);
        }
    }

    @Test
    void failedRebuildRestoresOriginalStatusAndRebuildsRestoredWhitelist() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 1);
        KnowledgeChunk original = chunk(11L, 1L, 0);
        persistence.chunks.add(original);
        index.failuresRemaining = 1;

        assertCode(() -> service.changeStatus(1L, "DISABLED"),
                "KNOWLEDGE_INDEX_REBUILD_FAILED", 500);

        assertThat(persistence.document(1L).status()).isEqualTo(KnowledgeDocumentStatus.ACTIVE);
        assertThat(index.rebuilds).containsExactly(List.of(), List.of(original));
        assertThat(persistence.statusUpdates).isEqualTo(2);
    }

    @Test
    void failedStatusCompensationIsObservableAndNeverReportedAsSuccess() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 1);
        persistence.chunks.add(chunk(11L, 1L, 0));
        index.failuresRemaining = 1;
        persistence.failRestore = true;

        assertCode(() -> service.changeStatus(1L, "DISABLED"),
                "KNOWLEDGE_ADMIN_COMPENSATION_FAILED", 500);
    }

    @Test
    void activeAndIndexingDocumentsMustNotBeDeleted() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "active.txt"), 1);
        persistence.seed(document(2L, KnowledgeDocumentStatus.INDEXING, "indexing.txt"), 0);

        assertCode(() -> service.deleteDocument(1L),
                "KNOWLEDGE_DOCUMENT_STATE_CONFLICT", 409);
        assertCode(() -> service.deleteDocument(2L),
                "KNOWLEDGE_DOCUMENT_STATE_CONFLICT", 409);
        assertThat(persistence.deleteCalls).isZero();
    }

    @Test
    void disabledAndFailedDocumentsDeleteDatabaseRowsAndControlledFiles() throws Exception {
        Files.createDirectories(uploadRoot);
        for (long id : List.of(1L, 2L)) {
            String filename = "safe-" + id + ".txt";
            Files.writeString(uploadRoot.resolve(filename), "controlled");
            KnowledgeDocumentStatus status = id == 1
                    ? KnowledgeDocumentStatus.DISABLED : KnowledgeDocumentStatus.FAILED;
            persistence.seed(document(id, status, filename), 1);
            persistence.chunks.add(chunk(id * 10, id, 0));

            KnowledgeDocumentDeletionResponse response = service.deleteDocument(id);

            assertThat(response.id()).isEqualTo(id);
            assertThat(response.deleted()).isTrue();
            assertThat(uploadRoot.resolve(filename)).doesNotExist();
            assertThat(persistence.findDocument(id)).isEmpty();
            assertThat(persistence.chunks).noneMatch(chunk -> chunk.documentId().equals(id));
        }
        assertThat(index.rebuilds).containsExactly(List.of(), List.of());
    }

    @Test
    void failedPreDeleteIndexReconciliationLeavesFileAndDatabaseUntouched() throws Exception {
        Files.createDirectories(uploadRoot);
        Path file = uploadRoot.resolve("safe.txt");
        Files.writeString(file, "controlled");
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 0);
        index.failuresRemaining = 1;

        assertCode(() -> service.deleteDocument(1L),
                "KNOWLEDGE_INDEX_REBUILD_FAILED", 500);

        assertThat(file).hasContent("controlled");
        assertThat(uploadRoot.resolve(".deleting")).doesNotExist();
        assertThat(persistence.findDocument(1L)).isPresent();
        assertThat(persistence.deleteCalls).isZero();
    }

    @Test
    void databaseDeleteFailureRestoresQuarantinedFile() throws Exception {
        Files.createDirectories(uploadRoot);
        Path file = uploadRoot.resolve("safe.txt");
        Files.writeString(file, "controlled");
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 0);
        persistence.deleteReturnsFalse = true;

        assertCode(() -> service.deleteDocument(1L),
                "KNOWLEDGE_DOCUMENT_STATE_CONFLICT", 409);

        assertThat(file).hasContent("controlled");
        assertThat(uploadRoot.resolve(".deleting")).isEmptyDirectory();
        assertThat(persistence.findDocument(1L)).isPresent();
    }

    @Test
    void storedFilenameCannotEscapeConfiguredUploadRoot() throws Exception {
        Path outside = temporaryDirectory.resolve("outside.txt");
        Files.writeString(outside, "must survive");
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "..\\outside.txt"), 0);

        assertCode(() -> service.deleteDocument(1L),
                "KNOWLEDGE_DOCUMENT_STORAGE_INVALID", 500);

        assertThat(outside).hasContent("must survive");
        assertThat(persistence.deleteCalls).isZero();
    }

    @Test
    void nonRegularStoredPathIsNeverMovedOrDeleted() throws Exception {
        Files.createDirectories(uploadRoot.resolve("safe.txt"));
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 0);

        assertCode(() -> service.deleteDocument(1L),
                "KNOWLEDGE_DOCUMENT_STORAGE_INVALID", 500);

        assertThat(uploadRoot.resolve("safe.txt")).isDirectory();
        assertThat(persistence.deleteCalls).isZero();
    }

    @Test
    void cleanupFailureAfterCommittedDeleteIsLoggedOperationalDebtButNotFalseDatabaseFailure()
            throws Exception {
        Files.createDirectories(uploadRoot);
        Files.writeString(uploadRoot.resolve("safe.txt"), "controlled");
        persistence.seed(document(1L, KnowledgeDocumentStatus.DISABLED, "safe.txt"), 0);
        KnowledgeAdministrationFileOperations cleanupFailing =
                new DelegatingFileOperations() {
                    @Override
                    public void deleteIfExists(Path path) throws IOException {
                        throw new IOException("C:\\private\\quarantine API_KEY");
                    }
                };

        KnowledgeDocumentDeletionResponse response =
                service(cleanupFailing).deleteDocument(1L);

        assertThat(response.deleted()).isTrue();
        assertThat(persistence.findDocument(1L)).isEmpty();
    }

    @Test
    void explicitReindexAcceptsEmptyWhitelistAndReportsSafeSummary() {
        KnowledgeReindexResponse response = service.reindex();

        assertThat(response.activeDocumentCount()).isZero();
        assertThat(response.chunkCount()).isZero();
        assertThat(response.rebuiltAt()).isEqualTo(NOW);
        assertThat(index.rebuilds).containsExactly(List.of());
    }

    @Test
    void explicitReindexUsesOnlyActiveDocumentsAndMapsConcurrentVersionToConflict() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "active.txt"), 1);
        persistence.seed(document(2L, KnowledgeDocumentStatus.DISABLED, "disabled.txt"), 1);
        KnowledgeChunk active = chunk(11L, 1L, 0);
        persistence.chunks.addAll(List.of(active, chunk(21L, 2L, 0)));

        KnowledgeReindexResponse response = service.reindex();

        assertThat(response.activeDocumentCount()).isEqualTo(1);
        assertThat(response.chunkCount()).isEqualTo(1);
        assertThat(index.rebuilds).containsExactly(List.of(active));

        index.failureCode = "KNOWLEDGE_INDEX_CONCURRENT_UPDATE";
        index.failuresRemaining = 1;
        assertCode(service::reindex, "KNOWLEDGE_INDEX_REBUILD_FAILED", 409);
    }

    @Test
    void missingDocumentUsesStableNotFoundError() {
        assertCode(() -> service.getDocument(99L),
                "KNOWLEDGE_DOCUMENT_NOT_FOUND", 404);
        assertCode(() -> service.getChunks(99L, 0, 20),
                "KNOWLEDGE_DOCUMENT_NOT_FOUND", 404);
        assertCode(() -> service.changeStatus(99L, "ACTIVE"),
                "KNOWLEDGE_DOCUMENT_NOT_FOUND", 404);
        assertCode(() -> service.deleteDocument(99L),
                "KNOWLEDGE_DOCUMENT_NOT_FOUND", 404);
    }

    @Test
    void chunkPageIsBoundedAndMappedWithoutEmbeddingIdentifiers() {
        persistence.seed(document(1L, KnowledgeDocumentStatus.ACTIVE, "safe.txt"), 2);
        persistence.chunks.addAll(List.of(chunk(11L, 1L, 0), chunk(12L, 1L, 1)));

        KnowledgeChunkPageResponse page = service.getChunks(1L, 0, 1);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(11L);
            assertThat(item.content()).isEqualTo("正文-0");
        });
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertCode(() -> service.getChunks(1L, -1, 20),
                "INVALID_KNOWLEDGE_CHUNK_PAGE", 400);
        assertCode(() -> service.getChunks(1L, 0, 101),
                "INVALID_KNOWLEDGE_CHUNK_PAGE", 400);
    }

    private KnowledgeAdministrationService service(KnowledgeAdministrationFileOperations fileOperations) {
        return new KnowledgeAdministrationService(
                persistence,
                index,
                new KnowledgeUploadProperties(uploadRoot, DataSize.ofMegabytes(1), 1000),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fileOperations);
    }

    private void assertCode(Runnable action, String code, int status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(KnowledgeAdministrationException.class)
                .satisfies(error -> {
                    KnowledgeAdministrationException exception =
                            (KnowledgeAdministrationException) error;
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.status().value()).isEqualTo(status);
                    assertThat(exception.getMessage())
                            .doesNotContain("C:\\", "/private", "API_KEY", "正文-", "knowledge_document");
                });
    }

    private KnowledgeDocument document(Long id, KnowledgeDocumentStatus status, String storedFilename) {
        return new KnowledgeDocument(
                id, "杭州知识", "杭州", "文旅局",
                URI.create("https://example.test/source/" + id),
                LocalDate.of(2026, 9, 1), KnowledgeDocumentType.OFFICIAL_NOTICE,
                "guide.txt", storedFilename, "text/plain", 128,
                String.valueOf((char) ('a' + Math.toIntExact(id % 6))).repeat(64),
                status, status == KnowledgeDocumentStatus.FAILED ? "safe failure" : null,
                9, NOW.minusSeconds(id), NOW.minusSeconds(id));
    }

    private KnowledgeChunk chunk(Long id, Long documentId, int chunkIndex) {
        return new KnowledgeChunk(id, documentId, chunkIndex, "正文-" + chunkIndex,
                "knowledge:" + documentId + ":chunk:" + chunkIndex, NOW);
    }

    private final class FakePersistence implements KnowledgeAdministrationPersistence {
        private final Map<Long, DocumentRecord> documents = new LinkedHashMap<>();
        private final List<KnowledgeChunk> chunks = new ArrayList<>();
        private int statusUpdates;
        private int deleteCalls;
        private boolean failRestore;
        private boolean deleteReturnsFalse;

        void seed(KnowledgeDocument document, long chunkCount) {
            documents.put(document.id(), new DocumentRecord(document, chunkCount));
        }

        KnowledgeDocument document(Long id) {
            return documents.get(id).document();
        }

        @Override
        public List<DocumentRecord> findAllDocuments() {
            return List.copyOf(documents.values());
        }

        @Override
        public Optional<DocumentRecord> findDocument(Long id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public ChunkSlice findChunks(Long documentId, int page, int size) {
            List<KnowledgeChunk> all = chunks.stream()
                    .filter(chunk -> chunk.documentId().equals(documentId))
                    .sorted(java.util.Comparator.comparingInt(KnowledgeChunk::chunkIndex)
                            .thenComparing(KnowledgeChunk::id))
                    .toList();
            int from = Math.min(page * size, all.size());
            int to = Math.min(from + size, all.size());
            return new ChunkSlice(all.subList(from, to), all.size());
        }

        @Override
        public boolean updateStatus(Long id, KnowledgeDocumentStatus expected,
                                    KnowledgeDocumentStatus target, Instant updatedAt) {
            statusUpdates++;
            DocumentRecord current = documents.get(id);
            if (current == null || current.document().status() != expected
                    || (failRestore && statusUpdates > 1)) {
                return false;
            }
            KnowledgeDocument source = current.document();
            KnowledgeDocument changed = new KnowledgeDocument(
                    source.id(), source.title(), source.city(), source.publisher(), source.sourceUrl(),
                    source.publishedAt(), source.documentType(), source.originalFilename(),
                    source.storedFilename(), source.contentType(), source.fileSize(), source.sha256(),
                    target, source.failureReason(), source.uploadedBy(), source.createdAt(), updatedAt);
            documents.put(id, new DocumentRecord(changed, current.chunkCount()));
            return true;
        }

        @Override
        public boolean deleteDocument(Long id, KnowledgeDocumentStatus expected) {
            deleteCalls++;
            DocumentRecord current = documents.get(id);
            if (deleteReturnsFalse || current == null || current.document().status() != expected) {
                return false;
            }
            documents.remove(id);
            chunks.removeIf(chunk -> chunk.documentId().equals(id));
            return true;
        }

        @Override
        public ActiveSnapshot loadActiveSnapshot() {
            Set<Long> activeIds = documents.values().stream()
                    .map(DocumentRecord::document)
                    .filter(document -> document.status() == KnowledgeDocumentStatus.ACTIVE)
                    .map(KnowledgeDocument::id)
                    .collect(java.util.stream.Collectors.toSet());
            return new ActiveSnapshot(activeIds.size(), chunks.stream()
                    .filter(chunk -> activeIds.contains(chunk.documentId()))
                    .sorted(java.util.Comparator.comparing(KnowledgeChunk::documentId)
                            .thenComparingInt(KnowledgeChunk::chunkIndex))
                    .toList());
        }
    }

    private static final class RecordingIndex implements KnowledgeIndex {
        private final List<List<KnowledgeChunk>> rebuilds = new ArrayList<>();
        private int failuresRemaining;
        private String failureCode = "KNOWLEDGE_INDEX_PERSIST_FAILED";

        @Override
        public void rebuild(List<KnowledgeChunk> activeChunks) {
            rebuilds.add(List.copyOf(activeChunks));
            if (failuresRemaining-- > 0) {
                throw new KnowledgeIndexException(failureCode, "unsafe detail must be hidden");
            }
        }

        @Override public void addAll(List<KnowledgeChunk> chunks) { throw new UnsupportedOperationException(); }
        @Override public List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults) { return List.of(); }
        @Override public List<KnowledgeMatch> search(
                KnowledgeQuery query, Set<Long> allowedChunkIds, int maxResults) { return List.of(); }
    }

    private static class DelegatingFileOperations implements KnowledgeAdministrationFileOperations {
        private final NioKnowledgeAdministrationFileOperations delegate =
                new NioKnowledgeAdministrationFileOperations();

        @Override public void createDirectories(Path path) throws IOException {
            delegate.createDirectories(path);
        }
        @Override public boolean exists(Path path) {
            return delegate.exists(path);
        }
        @Override public boolean isRegularFile(Path path) {
            return delegate.isRegularFile(path);
        }
        @Override public void moveAtomically(Path source, Path target) throws IOException {
            delegate.moveAtomically(source, target);
        }
        @Override public void deleteIfExists(Path path) throws IOException {
            delegate.deleteIfExists(path);
        }
    }
}
