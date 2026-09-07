package com.wyx.travelagent.knowledge.ingest;

import com.wyx.travelagent.knowledge.admin.UploadedKnowledgeDocument;
import com.wyx.travelagent.knowledge.index.DeterministicFakeEmbeddingModel;
import com.wyx.travelagent.knowledge.index.KnowledgeIndexProperties;
import com.wyx.travelagent.knowledge.index.KnowledgeIngestionIndex;
import com.wyx.travelagent.knowledge.index.KnowledgeMatch;
import com.wyx.travelagent.knowledge.index.KnowledgeQuery;
import com.wyx.travelagent.knowledge.index.LangChain4jKnowledgeIndex;
import com.wyx.travelagent.knowledge.index.PreparedKnowledgeIndexBatch;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import com.wyx.travelagent.knowledge.repository.DuplicateKnowledgeDocumentException;
import com.wyx.travelagent.knowledge.repository.KnowledgeIngestionPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIngestionServiceTest {
    private static final String MODEL = "text-embedding-v3";

    @TempDir
    Path temporaryDirectory;

    private DeterministicFakeEmbeddingModel embeddingModel;
    private InMemoryPersistence persistence;
    private LangChain4jKnowledgeIndex index;
    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        embeddingModel = new DeterministicFakeEmbeddingModel(MODEL, 4);
        persistence = new InMemoryPersistence();
        index = new LangChain4jKnowledgeIndex(
                embeddingModel,
                MODEL,
                new KnowledgeIndexProperties(temporaryDirectory.resolve("knowledge-index.json")));
        service = new KnowledgeIngestionService(persistence, new TextChunker(), index);
    }

    @Test
    void movesUploadedThroughIndexingToActiveWithStableIdsAndNoLongDatabaseTransaction() {
        AtomicBoolean embeddingsReadyBeforeChunkTransaction = new AtomicBoolean();
        persistence.beforeSaveChunks = () -> embeddingsReadyBeforeChunkTransaction
                .set(!embeddingModel.embeddedTexts().isEmpty());

        KnowledgeDocument active = service.ingest(uploaded("a".repeat(900), digest('a')));

        assertThat(active.id()).isEqualTo(1L);
        assertThat(active.status()).isEqualTo(KnowledgeDocumentStatus.ACTIVE);
        assertThat(persistence.events).containsExactly(
                "find-by-sha256",
                "insert-uploaded",
                "transition:UPLOADED->INDEXING",
                "insert-all-chunks",
                "transition:INDEXING->ACTIVE");
        assertThat(embeddingsReadyBeforeChunkTransaction).isTrue();
        assertThat(persistence.savedChunks)
                .extracting(KnowledgeChunk::embeddingId)
                .containsExactly("knowledge:1:chunk:0", "knowledge:1:chunk:1");
        assertThat(persistence.savedChunks)
                .extracting(KnowledgeChunk::chunkIndex)
                .containsExactly(0, 1);
        assertThat(index.search(new KnowledgeQuery(persistence.savedChunks.getFirst().content()), 5))
                .extracting(KnowledgeMatch::documentId).contains(1L);
    }

    @Test
    void concurrentSearchWaitsForConditionalActiveTransitionBeforeSeeingCandidateSnapshot() throws Exception {
        CountDownLatch activationEntered = new CountDownLatch(1);
        CountDownLatch allowActivation = new CountDownLatch(1);
        persistence.activationEntered = activationEntered;
        persistence.allowActivation = allowActivation;
        String content = "ACTIVE 提交前不可见".repeat(100);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<KnowledgeDocument> ingestion =
                    executor.submit(() -> service.ingest(uploaded(content, digest('9'))));
            assertThat(activationEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<KnowledgeMatch>> search =
                    executor.submit(() -> index.search(new KnowledgeQuery(content), 5));
            try {
                assertThatThrownBy(() -> search.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                allowActivation.countDown();
            }
            assertThat(ingestion.get(5, TimeUnit.SECONDS).status())
                    .isEqualTo(KnowledgeDocumentStatus.ACTIVE);
            assertThat(search.get(5, TimeUnit.SECONDS))
                    .extracting(KnowledgeMatch::documentId)
                    .containsOnly(1L);
        }
        assertThat(index.search(new KnowledgeQuery(content), 5))
                .extracting(KnowledgeMatch::documentId)
                .containsOnly(1L);
    }

    @Test
    void treatsCommittedActiveTransitionAsSuccessWhenClientReceivesAnException() {
        persistence.throwAfterActivationCommit = true;
        String content = "提交成功但响应中断".repeat(100);

        KnowledgeDocument active = service.ingest(uploaded(content, digest('7')));

        assertThat(active.status()).isEqualTo(KnowledgeDocumentStatus.ACTIVE);
        assertThat(persistence.status(active.id())).isEqualTo(KnowledgeDocumentStatus.ACTIVE);
        assertThat(index.search(new KnowledgeQuery(content), 5))
                .extracting(KnowledgeMatch::documentId)
                .containsOnly(active.id());
    }

    @Test
    void rejectsKnownDuplicateHashBeforeCallingEmbeddingModel() {
        persistence.seedActive(document(8L, digest('b'), KnowledgeDocumentStatus.ACTIVE));

        assertThatThrownBy(() -> service.ingest(uploaded("重复正文", digest('b'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("DUPLICATE_KNOWLEDGE_DOCUMENT");

        assertThat(embeddingModel.embeddedTexts()).isEmpty();
        assertThat(persistence.events).containsExactly("find-by-sha256");
    }

    @Test
    void convertsConcurrentDatabaseUniqueViolationToSameSafeDuplicateErrorBeforeEmbedding() {
        persistence.failInsertAsDuplicate = true;

        assertThatThrownBy(() -> service.ingest(uploaded("并发重复正文", digest('c'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .hasMessage("knowledge document content already exists")
                .hasMessageNotContaining("knowledge_document")
                .extracting("code").isEqualTo("DUPLICATE_KNOWLEDGE_DOCUMENT");

        assertThat(embeddingModel.embeddedTexts()).isEmpty();
    }

    @Test
    void embeddingFailureMarksDocumentFailedAndWritesNeitherChunksNorIndex() {
        embeddingModel.failWhenTextContains("失败标记");

        assertThatThrownBy(() -> service.ingest(uploaded("失败标记".repeat(120), digest('d'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_EMBEDDING_FAILED");

        assertThat(persistence.status(1L)).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(persistence.savedChunks).isEmpty();
        assertThat(temporaryDirectory.resolve("knowledge-index.json")).doesNotExist();
    }

    @Test
    void failureOnLaterEmbeddingDoesNotPublishEarlierChunk() {
        String text = "正常片段".repeat(110) + "失败片段".repeat(110);
        embeddingModel.failWhenTextContains("失败片段");

        assertThatThrownBy(() -> service.ingest(uploaded(text, digest('e'))))
                .isInstanceOf(KnowledgeIngestionException.class);

        assertThat(persistence.savedChunks).isEmpty();
        assertThat(index.search(new KnowledgeQuery("正常片段"), 10)).isEmpty();
    }

    @Test
    void chunkTransactionFailureDoesNotPolluteFormalOrMemoryIndex() {
        persistence.failChunkInsert = true;

        assertThatThrownBy(() -> service.ingest(uploaded("数据库写入失败".repeat(100), digest('f'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_CHUNK_PERSIST_FAILED");

        assertThat(persistence.status(1L)).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(index.search(new KnowledgeQuery("数据库写入失败"), 5)).isEmpty();
        assertThat(temporaryDirectory.resolve("knowledge-index.json")).doesNotExist();
    }

    @Test
    void indexCommitFailureMarksFailedWithoutClaimingActive() {
        RecordingIndex failingIndex = new RecordingIndex();
        failingIndex.failCommit = true;
        service = new KnowledgeIngestionService(persistence, new TextChunker(), failingIndex);

        assertThatThrownBy(() -> service.ingest(uploaded("原子移动失败".repeat(100), digest('1'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_INDEX_PERSIST_FAILED");

        assertThat(persistence.status(1L)).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(persistence.events).doesNotContain("transition:INDEXING->ACTIVE");
    }

    @Test
    void activeTransitionFailureRebuildsFromDatabaseActiveChunksThenMarksFailed() {
        KnowledgeChunk previouslyActive = chunk(70L, 700L, 0, "已有 ACTIVE 正文");
        persistence.activeChunks.add(previouslyActive);
        persistence.failActivation = true;
        RecordingIndex recordingIndex = new RecordingIndex();
        service = new KnowledgeIngestionService(persistence, new TextChunker(), recordingIndex);

        assertThatThrownBy(() -> service.ingest(uploaded("不能最终激活".repeat(100), digest('2'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_DOCUMENT_ACTIVATION_FAILED");

        assertThat(recordingIndex.rebuiltWith).containsExactly(previouslyActive);
        assertThat(persistence.events).containsSubsequence(
                "transition:INDEXING->ACTIVE", "find-active-chunks", "transition:INDEXING->FAILED");
        assertThat(persistence.status(1L)).isEqualTo(KnowledgeDocumentStatus.FAILED);
    }

    @Test
    void activationRebuildFailureIsObservableAndSearchFailsClosed() {
        KnowledgeChunk previouslyActive = chunk(81L, 801L, 0, "补偿向量失败标记");
        index.addAll(List.of(previouslyActive));
        persistence.activeChunks.add(previouslyActive);
        persistence.failActivation = true;
        embeddingModel.failWhenTextContains("补偿向量失败标记");
        String incompleteContent = "不得提前可见的新文档".repeat(100);

        assertThatThrownBy(() ->
                service.ingest(uploaded(incompleteContent, digest('8'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code")
                .isEqualTo("KNOWLEDGE_INGESTION_COMPENSATION_FAILED");

        assertThat(persistence.status(1L)).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThatThrownBy(() -> index.search(new KnowledgeQuery(incompleteContent), 10))
                .isInstanceOf(com.wyx.travelagent.knowledge.index.KnowledgeIndexException.class)
                .extracting("code")
                .isEqualTo("KNOWLEDGE_INDEX_REBUILD_REQUIRED");
        assertThatThrownBy(() ->
                index.addAll(List.of(chunk(82L, 802L, 0, "补偿前禁止继续写入"))))
                .isInstanceOf(com.wyx.travelagent.knowledge.index.KnowledgeIndexException.class)
                .extracting("code")
                .isEqualTo("KNOWLEDGE_INDEX_REBUILD_REQUIRED");
    }

    @Test
    void savedFailureReasonIsStableAndContainsNoSensitiveCauseDetails() {
        persistence.chunkFailureMessage = "SQL knowledge_chunk /private/path API_KEY 正文向量";
        persistence.failChunkInsert = true;

        assertThatThrownBy(() -> service.ingest(uploaded("敏感正文".repeat(100), digest('3'))))
                .isInstanceOf(KnowledgeIngestionException.class);

        assertThat(persistence.failureReason).isEqualTo("knowledge chunk persistence failed")
                .doesNotContain("SQL", "knowledge_chunk", "/private/path", "API_KEY", "敏感正文", "向量");
    }

    @Test
    void explicitRebuildUsesOnlyPersistenceActiveChunkQuery() {
        KnowledgeChunk active = chunk(80L, 800L, 0, "数据库 ACTIVE 集合");
        persistence.activeChunks.add(active);
        RecordingIndex recordingIndex = new RecordingIndex();
        service = new KnowledgeIngestionService(persistence, new TextChunker(), recordingIndex);

        service.rebuildFromActiveDocuments();

        assertThat(recordingIndex.rebuiltWith).containsExactly(active);
        assertThat(persistence.events).containsExactly("find-active-chunks");
    }

    @Test
    void conditionalStateUpdateFailureDoesNotOverwriteConcurrentStatus() {
        persistence.failBeginIndexing = true;

        assertThatThrownBy(() -> service.ingest(uploaded("并发状态变化", digest('4'))))
                .isInstanceOf(KnowledgeIngestionException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_DOCUMENT_STATE_CONFLICT");

        assertThat(embeddingModel.embeddedTexts()).isEmpty();
        assertThat(persistence.events).doesNotContain("transition:INDEXING->FAILED");
    }

    private UploadedKnowledgeDocument uploaded(String text, String sha256) {
        return new UploadedKnowledgeDocument(
                document(null, sha256, KnowledgeDocumentStatus.UPLOADED), text);
    }

    private KnowledgeDocument document(Long id, String sha256, KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                id, "西湖说明", "杭州", "文旅局", URI.create("https://example.test/source"),
                LocalDate.of(2026, 8, 1), KnowledgeDocumentType.OFFICIAL_NOTICE,
                "guide.txt", "safe-guide.txt", "text/plain", 128, sha256,
                status, null, 9, Instant.EPOCH, Instant.EPOCH);
    }

    private KnowledgeChunk chunk(long documentId, long chunkId, int chunkIndex, String content) {
        return new KnowledgeChunk(chunkId, documentId, chunkIndex, content,
                "knowledge:" + documentId + ":chunk:" + chunkIndex, Instant.EPOCH);
    }

    private String digest(char character) {
        return String.valueOf(character).repeat(64);
    }

    private final class InMemoryPersistence implements KnowledgeIngestionPersistence {
        private final Map<Long, KnowledgeDocument> documents = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private final List<KnowledgeChunk> savedChunks = new ArrayList<>();
        private final List<KnowledgeChunk> activeChunks = new ArrayList<>();
        private long nextDocumentId = 1;
        private long nextChunkId = 100;
        private boolean failInsertAsDuplicate;
        private boolean failChunkInsert;
        private boolean failActivation;
        private boolean throwAfterActivationCommit;
        private boolean failBeginIndexing;
        private String chunkFailureMessage = "simulated chunk transaction failure";
        private String failureReason;
        private Runnable beforeSaveChunks = () -> { };
        private CountDownLatch activationEntered;
        private CountDownLatch allowActivation;

        @Override
        public Optional<KnowledgeDocument> findBySha256(String sha256) {
            events.add("find-by-sha256");
            return documents.values().stream().filter(document -> document.sha256().equals(sha256)).findFirst();
        }

        @Override
        public KnowledgeDocument insertUploaded(KnowledgeDocument document) {
            events.add("insert-uploaded");
            if (failInsertAsDuplicate) {
                throw new DuplicateKnowledgeDocumentException(new IllegalStateException("unique knowledge_document"));
            }
            KnowledgeDocument persisted = copy(document, nextDocumentId++, KnowledgeDocumentStatus.UPLOADED, null);
            documents.put(persisted.id(), persisted);
            return persisted;
        }

        @Override
        public boolean transition(Long documentId, KnowledgeDocumentStatus expected, KnowledgeDocumentStatus target) {
            events.add("transition:" + expected + "->" + target);
            if (target == KnowledgeDocumentStatus.ACTIVE && activationEntered != null) {
                activationEntered.countDown();
                try {
                    if (!allowActivation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("activation test timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("activation test interrupted", exception);
                }
            }
            if ((expected == KnowledgeDocumentStatus.UPLOADED && failBeginIndexing)
                    || (target == KnowledgeDocumentStatus.ACTIVE && failActivation)) {
                return false;
            }
            KnowledgeDocument current = documents.get(documentId);
            if (current == null || current.status() != expected) {
                return false;
            }
            documents.put(documentId, copy(current, documentId, target, current.failureReason()));
            if (target == KnowledgeDocumentStatus.ACTIVE && throwAfterActivationCommit) {
                activeChunks.addAll(savedChunks.stream()
                        .filter(chunk -> chunk.documentId().equals(documentId))
                        .toList());
                throw new IllegalStateException("simulated response loss after commit");
            }
            return true;
        }

        @Override
        public List<KnowledgeChunk> insertAllChunks(List<KnowledgeChunk> chunks) {
            events.add("insert-all-chunks");
            beforeSaveChunks.run();
            if (failChunkInsert) {
                throw new IllegalStateException(chunkFailureMessage);
            }
            List<KnowledgeChunk> persisted = chunks.stream()
                    .map(chunk -> new KnowledgeChunk(nextChunkId++, chunk.documentId(), chunk.chunkIndex(),
                            chunk.content(), chunk.embeddingId(), chunk.createdAt()))
                    .toList();
            savedChunks.addAll(persisted);
            return persisted;
        }

        @Override
        public boolean markFailed(Long documentId, String reason) {
            failureReason = reason;
            return transition(documentId, KnowledgeDocumentStatus.INDEXING, KnowledgeDocumentStatus.FAILED);
        }

        @Override
        public List<KnowledgeChunk> findAllActiveChunks() {
            events.add("find-active-chunks");
            return List.copyOf(activeChunks);
        }

        @Override
        public void deleteChunks(Long documentId) {
            savedChunks.removeIf(chunk -> chunk.documentId().equals(documentId));
        }

        private void seedActive(KnowledgeDocument document) {
            documents.put(document.id(), document);
        }

        private KnowledgeDocumentStatus status(Long documentId) {
            return documents.get(documentId).status();
        }

        private KnowledgeDocument copy(
                KnowledgeDocument source, Long id, KnowledgeDocumentStatus status, String failureReason) {
            return new KnowledgeDocument(
                    id, source.title(), source.city(), source.publisher(), source.sourceUrl(), source.publishedAt(),
                    source.documentType(), source.originalFilename(), source.storedFilename(), source.contentType(),
                    source.fileSize(), source.sha256(), status, failureReason, source.uploadedBy(),
                    source.createdAt(), source.updatedAt());
        }
    }

    private static final class RecordingIndex implements KnowledgeIngestionIndex {
        private boolean failCommit;
        private List<KnowledgeChunk> rebuiltWith = List.of();

        @Override
        public PreparedKnowledgeIndexBatch prepare(List<KnowledgeChunk> chunks) {
            return new PreparedKnowledgeIndexBatch() { };
        }

        @Override
        public void addPrepared(PreparedKnowledgeIndexBatch batch, List<KnowledgeChunk> persistedChunks) {
            if (failCommit) {
                throw new com.wyx.travelagent.knowledge.index.KnowledgeIndexException(
                        "KNOWLEDGE_INDEX_PERSIST_FAILED", "knowledge index could not be atomically persisted");
            }
        }

        @Override
        public boolean addPreparedAndActivate(
                PreparedKnowledgeIndexBatch batch,
                List<KnowledgeChunk> persistedChunks,
                BooleanSupplier activation) {
            addPrepared(batch, persistedChunks);
            return activation.getAsBoolean();
        }

        @Override
        public void addAll(List<KnowledgeChunk> chunks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults) {
            return List.of();
        }

        @Override
        public void rebuild(List<KnowledgeChunk> activeChunks) {
            rebuiltWith = List.copyOf(activeChunks);
        }
    }
}
