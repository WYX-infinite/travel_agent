package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jKnowledgeIndexTest {
    private static final String MODEL = "text-embedding-v3";

    @TempDir
    Path temporaryDirectory;

    @Test
    void startsEmptyWhenFormalIndexDoesNotExist() {
        LangChain4jKnowledgeIndex index = index(new DeterministicFakeEmbeddingModel(MODEL, 4));

        assertThat(index.search(new KnowledgeQuery("任意查询"), 5)).isEmpty();
        assertThat(temporaryDirectory.resolve("knowledge-index.json")).doesNotExist();
    }

    @Test
    void persistsVersionModelDimensionAndEntriesThenReloadsSameSearchResult() throws Exception {
        KnowledgeChunk chunk = chunk(11, 101, 0, "西湖游览需要预留充足步行时间");
        LangChain4jKnowledgeIndex first = index(new DeterministicFakeEmbeddingModel(MODEL, 4));

        first.addAll(List.of(chunk));
        List<KnowledgeMatch> beforeRestart = first.search(new KnowledgeQuery(chunk.content()), 3);
        byte[] persisted = Files.readAllBytes(formalFile());

        LangChain4jKnowledgeIndex restarted = index(new DeterministicFakeEmbeddingModel(MODEL, 4));
        List<KnowledgeMatch> afterRestart = restarted.search(new KnowledgeQuery(chunk.content()), 3);

        assertThat(beforeRestart).containsExactlyElementsOf(afterRestart);
        assertThat(afterRestart).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo("knowledge:11:chunk:0");
            assertThat(match.documentId()).isEqualTo(11L);
            assertThat(match.chunkId()).isEqualTo(101L);
            assertThat(match.content()).isEqualTo(chunk.content());
        });
        String json = new String(persisted, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("\"formatVersion\":1", "\"embeddingModel\":\"" + MODEL + "\"",
                "\"vectorDimension\":4", "\"embeddingId\":\"knowledge:11:chunk:0\"",
                "\"documentId\":11", "\"chunkId\":101", "\"chunkIndex\":0", "\"vector\"");
    }

    @Test
    void ignoresOrphanTemporaryFileAndNeverPromotesIt() throws Exception {
        Files.writeString(temporaryDirectory.resolve("knowledge-index.json.tmp"), "partial-json");

        LangChain4jKnowledgeIndex index = index(new DeterministicFakeEmbeddingModel(MODEL, 4));

        assertThat(index.search(new KnowledgeQuery("query"), 3)).isEmpty();
        assertThat(formalFile()).doesNotExist();
        assertThat(temporaryDirectory.resolve("knowledge-index.json.tmp")).hasContent("partial-json");
    }

    @Test
    void writesFixedTemporaryFileBeforeAtomicMove() {
        AtomicBoolean sawTemporaryFile = new AtomicBoolean();
        AtomicIndexFileMover mover = (temporary, formal) -> {
            sawTemporaryFile.set(Files.isRegularFile(temporary)
                    && temporary.getFileName().toString().equals("knowledge-index.json.tmp"));
            Files.move(temporary, formal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        };
        LangChain4jKnowledgeIndex index = index(
                new DeterministicFakeEmbeddingModel(MODEL, 4), new JacksonKnowledgeIndexSnapshotCodec(), mover);

        index.addAll(List.of(chunk(12, 102, 0, "原子替换测试正文")));

        assertThat(sawTemporaryFile).isTrue();
        assertThat(formalFile()).isRegularFile();
    }

    @Test
    void atomicMoveFailurePreservesOldFormalBytesAndOldSearchSnapshot() throws Exception {
        AtomicReference<Boolean> failMove = new AtomicReference<>(false);
        AtomicIndexFileMover mover = (temporary, formal) -> {
            if (failMove.get()) {
                throw new AtomicMoveNotSupportedException(temporary.toString(), formal.toString(), "simulated");
            }
            Files.move(temporary, formal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        };
        LangChain4jKnowledgeIndex index = index(
                new DeterministicFakeEmbeddingModel(MODEL, 4), new JacksonKnowledgeIndexSnapshotCodec(), mover);
        KnowledgeChunk oldChunk = chunk(13, 103, 0, "旧索引正文");
        index.addAll(List.of(oldChunk));
        byte[] oldBytes = Files.readAllBytes(formalFile());
        failMove.set(true);

        assertThatThrownBy(() -> index.addAll(List.of(chunk(14, 104, 0, "不能暴露的新正文"))))
                .isInstanceOf(KnowledgeIndexException.class)
                .hasMessageNotContaining(temporaryDirectory.toString());

        assertThat(Files.readAllBytes(formalFile())).isEqualTo(oldBytes);
        assertThat(index.search(new KnowledgeQuery(oldChunk.content()), 5))
                .extracting(KnowledgeMatch::documentId).containsExactly(13L);
        assertThat(index.search(new KnowledgeQuery("不能暴露的新正文"), 5))
                .extracting(KnowledgeMatch::documentId).doesNotContain(14L);
    }

    @Test
    void temporaryFileWriteFailurePreservesOldFormalBytesAndOldSearchSnapshot() throws Exception {
        LangChain4jKnowledgeIndex index = index(new DeterministicFakeEmbeddingModel(MODEL, 4));
        KnowledgeChunk oldChunk = chunk(131, 1301, 0, "临时文件失败前的旧正文");
        index.addAll(List.of(oldChunk));
        byte[] oldBytes = Files.readAllBytes(formalFile());
        Files.createDirectory(temporaryDirectory.resolve("knowledge-index.json.tmp"));

        assertThatThrownBy(() ->
                index.addAll(List.of(chunk(132, 1302, 0, "临时文件无法写入的新正文"))))
                .isInstanceOf(KnowledgeIndexException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_INDEX_PERSIST_FAILED");

        assertThat(Files.readAllBytes(formalFile())).isEqualTo(oldBytes);
        assertThat(index.search(new KnowledgeQuery(oldChunk.content()), 5))
                .extracting(KnowledgeMatch::documentId).containsExactly(131L);
        assertThat(index.search(new KnowledgeQuery("临时文件无法写入的新正文"), 5))
                .extracting(KnowledgeMatch::documentId).doesNotContain(132L);
    }

    @Test
    void serializationFailurePreservesFormalBytesAndMemorySnapshot() throws Exception {
        AtomicBoolean failEncoding = new AtomicBoolean();
        KnowledgeIndexSnapshotCodec codec = new JacksonKnowledgeIndexSnapshotCodec() {
            @Override
            public byte[] encode(KnowledgeIndexSnapshotFile snapshot) {
                if (failEncoding.get()) {
                    throw new KnowledgeIndexException("KNOWLEDGE_INDEX_SERIALIZATION_FAILED",
                            "knowledge index serialization failed");
                }
                return super.encode(snapshot);
            }
        };
        LangChain4jKnowledgeIndex index = index(
                new DeterministicFakeEmbeddingModel(MODEL, 4), codec, AtomicIndexFileMover.nio());
        KnowledgeChunk oldChunk = chunk(15, 105, 0, "稳定旧正文");
        index.addAll(List.of(oldChunk));
        byte[] oldBytes = Files.readAllBytes(formalFile());
        failEncoding.set(true);

        assertThatThrownBy(() -> index.addAll(List.of(chunk(16, 106, 0, "序列化失败正文"))))
                .isInstanceOf(KnowledgeIndexException.class);

        assertThat(Files.readAllBytes(formalFile())).isEqualTo(oldBytes);
        assertThat(index.search(new KnowledgeQuery(oldChunk.content()), 3))
                .extracting(KnowledgeMatch::documentId).containsExactly(15L);
    }

    @Test
    void corruptedOrTruncatedFormalFileFailsClosed() throws Exception {
        Files.writeString(formalFile(), "{\"formatVersion\":1,");

        assertThatThrownBy(() -> index(new DeterministicFakeEmbeddingModel(MODEL, 4)))
                .isInstanceOf(KnowledgeIndexException.class)
                .hasMessageContaining("could not be loaded");
        assertThat(formalFile()).hasContent("{\"formatVersion\":1,");
    }

    @Test
    void unsupportedFormalFormatVersionFailsClosedWithoutRewritingFile() throws Exception {
        String unsupported = """
                {"formatVersion":99,"embeddingModel":"text-embedding-v3",
                 "vectorDimension":0,"entries":[]}
                """;
        Files.writeString(formalFile(), unsupported);

        assertThatThrownBy(() -> index(new DeterministicFakeEmbeddingModel(MODEL, 4)))
                .isInstanceOf(KnowledgeIndexException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_INDEX_FORMAT_INVALID");
        assertThat(formalFile()).hasContent(unsupported);
    }

    @Test
    void rebuildReplacesWholeSnapshotAndRemovesChunksMissingFromActiveSet() {
        LangChain4jKnowledgeIndex index = index(new DeterministicFakeEmbeddingModel(MODEL, 4));
        KnowledgeChunk removed = chunk(21, 201, 0, "即将被移除的文档");
        KnowledgeChunk retained = chunk(22, 202, 0, "仍然启用的文档");
        index.addAll(List.of(removed, retained));

        index.rebuild(List.of(retained));

        assertThat(index.search(new KnowledgeQuery(removed.content()), 10))
                .extracting(KnowledgeMatch::documentId).doesNotContain(21L);
        assertThat(index.search(new KnowledgeQuery(retained.content()), 10))
                .extracting(KnowledgeMatch::documentId).containsExactly(22L);
    }

    @Test
    void modelMismatchRejectsIncrementalWriteButAllowsFullRebuild() {
        KnowledgeChunk original = chunk(31, 301, 0, "旧模型正文");
        index(new DeterministicFakeEmbeddingModel("old-model", 4)).addAll(List.of(original));
        LangChain4jKnowledgeIndex changed = index(new DeterministicFakeEmbeddingModel("new-model", 4), "new-model");

        assertThatThrownBy(() -> changed.addAll(List.of(chunk(32, 302, 0, "新模型增量"))))
                .isInstanceOf(KnowledgeIndexException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_INDEX_MODEL_MISMATCH");

        KnowledgeChunk rebuilt = chunk(33, 303, 0, "新模型全量重建");
        changed.rebuild(List.of(rebuilt));
        assertThat(changed.search(new KnowledgeQuery(rebuilt.content()), 3))
                .extracting(KnowledgeMatch::documentId).containsExactly(33L);
    }

    @Test
    void vectorDimensionMismatchRejectsIncrementalWrite() {
        index(new DeterministicFakeEmbeddingModel(MODEL, 4))
                .addAll(List.of(chunk(41, 401, 0, "四维正文")));
        LangChain4jKnowledgeIndex changedDimension = index(
                new DeterministicFakeEmbeddingModel(MODEL, 3));

        assertThatThrownBy(() -> changedDimension.addAll(List.of(chunk(42, 402, 0, "三维正文"))))
                .isInstanceOf(KnowledgeIndexException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_INDEX_DIMENSION_MISMATCH");
    }

    @Test
    void sameActiveChunksProduceDeterministicFileBytes() throws Exception {
        LangChain4jKnowledgeIndex index = index(new DeterministicFakeEmbeddingModel(MODEL, 4));
        List<KnowledgeChunk> chunks = List.of(
                chunk(52, 502, 1, "第二个稳定切片"),
                chunk(51, 501, 0, "第一个稳定切片"));

        index.rebuild(chunks);
        byte[] first = Files.readAllBytes(formalFile());
        index.rebuild(chunks.reversed());
        byte[] second = Files.readAllBytes(formalFile());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void concurrentSearchWaitsUntilAtomicCommitPublishesOneConsistentSnapshot() throws Exception {
        CountDownLatch moveEntered = new CountDownLatch(1);
        CountDownLatch allowMove = new CountDownLatch(1);
        AtomicBoolean block = new AtomicBoolean(false);
        AtomicIndexFileMover mover = (temporary, formal) -> {
            if (block.get()) {
                moveEntered.countDown();
                try {
                    if (!allowMove.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("test timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test interrupted", exception);
                }
            }
            Files.move(temporary, formal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        };
        LangChain4jKnowledgeIndex index = index(
                new DeterministicFakeEmbeddingModel(MODEL, 4), new JacksonKnowledgeIndexSnapshotCodec(), mover);
        KnowledgeChunk oldChunk = chunk(61, 601, 0, "并发旧正文");
        KnowledgeChunk newChunk = chunk(62, 602, 0, "并发新正文");
        index.addAll(List.of(oldChunk));
        block.set(true);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> update = executor.submit(() -> index.addAll(List.of(newChunk)));
            assertThat(moveEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<KnowledgeMatch>> search =
                    executor.submit(() -> index.search(new KnowledgeQuery(newChunk.content()), 5));
            try {
                assertThatThrownBy(() -> search.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                allowMove.countDown();
            }
            update.get(5, TimeUnit.SECONDS);
            assertThat(search.get(5, TimeUnit.SECONDS))
                    .extracting(KnowledgeMatch::documentId).contains(62L);
        }
        assertThat(index.search(new KnowledgeQuery(newChunk.content()), 5))
                .extracting(KnowledgeMatch::documentId).contains(62L);
    }

    @Test
    void staleConcurrentRebuildCannotOverwriteCompletedIncrementalWrite() throws Exception {
        DeterministicFakeEmbeddingModel model = new DeterministicFakeEmbeddingModel(MODEL, 4);
        LangChain4jKnowledgeIndex index = index(model);
        KnowledgeChunk retained = chunk(71, 701, 0, "保留的旧正文");
        KnowledgeChunk staleRebuild = chunk(72, 702, 0, "阻塞的重建正文");
        KnowledgeChunk concurrentAdd = chunk(73, 703, 0, "并发完成的新增正文");
        index.addAll(List.of(retained));
        CountDownLatch embeddingEntered = new CountDownLatch(1);
        CountDownLatch allowEmbedding = new CountDownLatch(1);
        model.blockWhenTextContains(staleRebuild.content(), embeddingEntered, allowEmbedding);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> rebuild = executor.submit(() -> index.rebuild(List.of(staleRebuild)));
            assertThat(embeddingEntered.await(5, TimeUnit.SECONDS)).isTrue();

            index.addAll(List.of(concurrentAdd));
            allowEmbedding.countDown();

            assertThatThrownBy(() -> rebuild.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(KnowledgeIndexException.class)
                    .rootCause()
                    .extracting("code")
                    .isEqualTo("KNOWLEDGE_INDEX_CONCURRENT_UPDATE");
        }
        assertThat(index.search(new KnowledgeQuery(concurrentAdd.content()), 10))
                .extracting(KnowledgeMatch::documentId)
                .contains(71L, 73L)
                .doesNotContain(72L);
    }

    private LangChain4jKnowledgeIndex index(DeterministicFakeEmbeddingModel model) {
        return index(model, MODEL);
    }

    private LangChain4jKnowledgeIndex index(DeterministicFakeEmbeddingModel model, String configuredModel) {
        return new LangChain4jKnowledgeIndex(model, configuredModel,
                new KnowledgeIndexProperties(formalFile()));
    }

    private LangChain4jKnowledgeIndex index(
            DeterministicFakeEmbeddingModel model,
            KnowledgeIndexSnapshotCodec codec,
            AtomicIndexFileMover mover) {
        return new LangChain4jKnowledgeIndex(model, MODEL,
                new KnowledgeIndexProperties(formalFile()), codec, mover);
    }

    private Path formalFile() {
        return temporaryDirectory.resolve("knowledge-index.json");
    }

    private KnowledgeChunk chunk(long documentId, long chunkId, int chunkIndex, String content) {
        return new KnowledgeChunk(chunkId, documentId, chunkIndex, content,
                "knowledge:" + documentId + ":chunk:" + chunkIndex, Instant.EPOCH);
    }
}
