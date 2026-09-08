package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;

/**
 * 使用 LangChain4j {@link EmbeddingModel} 和 {@link InMemoryEmbeddingStore} 实现版本化磁盘索引。
 * <p>
 * 输入为数据库已持久化的知识切片，输出为基础相似度匹配；正式 JSON 同时记录格式版本、模型标识、
 * 向量维度、数据库关联和正文。上传与数据库状态机由入库服务负责；本类只执行数据库提供的
 * 切片白名单约束，不解释城市或 ACTIVE 语义，也不负责阈值、Top 8/Top 5 与引用校验规则。
 * <p>
 * 本地正式文件采用“单 JVM、单写者”部署前置条件；进程内读写锁不能协调多个应用实例共享同一路径。
 * 多实例部署必须由外部单一写入者或后续独立向量存储提供协调，不能把本类误当作分布式文件锁。
 */
public final class LangChain4jKnowledgeIndex implements KnowledgeIngestionIndex {
    static final int FORMAT_VERSION = 1;
    private static final Comparator<KnowledgeIndexEntryFile> ENTRY_ORDER = Comparator
            .comparing(KnowledgeIndexEntryFile::documentId)
            .thenComparingInt(KnowledgeIndexEntryFile::chunkIndex)
            .thenComparing(KnowledgeIndexEntryFile::embeddingId);

    private final EmbeddingModel embeddingModel;
    private final String configuredModel;
    private final KnowledgeIndexProperties properties;
    private final KnowledgeIndexSnapshotCodec codec;
    private final AtomicIndexFileMover mover;
    private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();
    private final Lock readerLock = snapshotLock.readLock();
    private final Lock writerLock = snapshotLock.writeLock();
    private volatile RuntimeSnapshot snapshot;
    private boolean rebuildRequired;

    /**
     * 创建生产索引并只从正式文件加载快照。
     * <p>
     * 孤立 {@code .tmp} 可能是进程在写入中途退出留下的半成品，所以启动时绝不读取或提升它。
     *
     * @param embeddingModel 由 Spring 提供的生产 EmbeddingModel；调用 add/rebuild/search 时可能访问远程模型
     * @param configuredModel 当前配置的稳定模型标识
     * @param properties 正式与临时索引路径
     * @throws KnowledgeIndexException 正式文件存在但损坏、截断或格式不兼容时抛出并失败关闭
     */
    public LangChain4jKnowledgeIndex(
            EmbeddingModel embeddingModel,
            String configuredModel,
            KnowledgeIndexProperties properties) {
        this(embeddingModel, configuredModel, properties,
                new JacksonKnowledgeIndexSnapshotCodec(), AtomicIndexFileMover.nio());
    }

    LangChain4jKnowledgeIndex(
            EmbeddingModel embeddingModel,
            String configuredModel,
            KnowledgeIndexProperties properties,
            KnowledgeIndexSnapshotCodec codec,
            AtomicIndexFileMover mover) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.configuredModel = requireText(configuredModel, "configuredModel");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.snapshot = loadFormalSnapshot();
    }

    @Override
    public void addAll(List<KnowledgeChunk> chunks) {
        PreparedKnowledgeIndexBatch prepared = prepare(chunks);
        if (chunks.isEmpty()) {
            return;
        }
        addPrepared(prepared, chunks);
    }

    @Override
    public PreparedKnowledgeIndexBatch prepare(List<KnowledgeChunk> chunks) {
        RuntimeSnapshot current = snapshot;
        requireIncrementalModelCompatibility(current);
        return new PreparedBatch(this, configuredModel, embedChunks(chunks, false));
    }

    @Override
    public void addPrepared(PreparedKnowledgeIndexBatch batch, List<KnowledgeChunk> persistedChunks) {
        if (!(batch instanceof PreparedBatch prepared) || prepared.owner != this) {
            throw new IllegalArgumentException("prepared batch belongs to another knowledge index");
        }
        List<KnowledgeIndexEntryFile> embedded = bindPersistedChunks(prepared, persistedChunks);
        if (embedded.isEmpty()) {
            return;
        }

        writerLock.lock();
        try {
            requireWriterReady();
            RuntimeSnapshot candidate = incrementalCandidate(embedded);
            persistAndPublish(candidate);
        } finally {
            writerLock.unlock();
        }
    }

    @Override
    public boolean addPreparedAndActivate(
            PreparedKnowledgeIndexBatch batch,
            List<KnowledgeChunk> persistedChunks,
            BooleanSupplier activation) {
        Objects.requireNonNull(activation, "activation");
        if (!(batch instanceof PreparedBatch prepared) || prepared.owner != this) {
            throw new IllegalArgumentException("prepared batch belongs to another knowledge index");
        }
        List<KnowledgeIndexEntryFile> embedded = bindPersistedChunks(prepared, persistedChunks);
        if (embedded.isEmpty()) {
            return activation.getAsBoolean();
        }

        writerLock.lock();
        try {
            requireWriterReady();
            RuntimeSnapshot candidate = incrementalCandidate(embedded);
            /*
             * 候选文件必须先完整原子落盘，并在同一个写锁临界区立即交换内存快照，确保磁盘和内存
             * 始终指向同一版本。搜索取得快照时需要短暂读锁，所以在下面的 ACTIVE 条件更新提交前
             * 不能观察候选；回调只执行一条短数据库事务，绝不包含远程 Embedding。
             */
            persist(candidate);
            snapshot = candidate;
            final boolean activated;
            try {
                activated = activation.getAsBoolean();
            } catch (RuntimeException exception) {
                rebuildRequired = true;
                throw exception;
            }
            if (!activated) {
                /*
                 * 正式文件和内存现在都是候选，而数据库仍可能非 ACTIVE。设置恢复标记后，搜索和新增
                 * 都会失败关闭，直到调用方用数据库 ACTIVE 全集 rebuild，避免暴露未完成文档或让
                 * 其他写请求跨过一致性恢复。
                 */
                rebuildRequired = true;
                return false;
            }
            return true;
        } finally {
            writerLock.unlock();
        }
    }

    @Override
    public List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults) {
        return searchInternal(query, null, maxResults);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<KnowledgeMatch> search(
            KnowledgeQuery query, Set<Long> allowedChunkIds, int maxResults) {
        Objects.requireNonNull(allowedChunkIds, "allowedChunkIds");
        if (allowedChunkIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("allowedChunkIds must contain only positive ids");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        if (allowedChunkIds.isEmpty()) {
            return List.of();
        }
        return searchInternal(query, Set.copyOf(allowedChunkIds), maxResults);
    }

    private List<KnowledgeMatch> searchInternal(
            KnowledgeQuery query, Set<Long> allowedChunkIds, int maxResults) {
        Objects.requireNonNull(query, "query");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        final RuntimeSnapshot current;
        readerLock.lock();
        try {
            /*
             * 读锁只用于取得一个不可变快照，不包围远程查询 Embedding。若 ACTIVE 更新失败而尚未
             * 完成补偿重建，搜索直接失败关闭，绝不读取已经落盘但尚未确认 ACTIVE 的候选版本。
             */
            requireWriterReady();
            current = snapshot;
        } finally {
            readerLock.unlock();
        }
        if (current.entries.isEmpty()) {
            return List.of();
        }
        requireIncrementalModelCompatibility(current);
        Embedding queryEmbedding = embedQuery(query.text());
        if (queryEmbedding.dimension() != current.vectorDimension) {
            throw dimensionMismatch();
        }

        List<EmbeddingMatch<TextSegment>> raw = current.store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(current.entries.size())
                        .minScore(0.0)
                        .build()).matches();
        return raw.stream()
                .map(match -> toKnowledgeMatch(match, current.byEmbeddingId))
                /* 数据库白名单是城市和 ACTIVE 状态的事实源，必须在 Top K 截断之前生效。 */
                .filter(match -> allowedChunkIds == null || allowedChunkIds.contains(match.chunkId()))
                .sorted(Comparator.comparingDouble(KnowledgeMatch::score).reversed()
                        .thenComparing(KnowledgeMatch::documentId)
                        .thenComparingInt(KnowledgeMatch::chunkIndex))
                .limit(maxResults)
                .toList();
    }

    @Override
    public void rebuild(List<KnowledgeChunk> activeChunks) {
        long expectedRevision = snapshot.revision;
        List<KnowledgeIndexEntryFile> embedded = embedChunks(activeChunks, true);

        writerLock.lock();
        try {
            /*
             * Embedding 远程调用发生在锁外。提交前再比较版本，可以阻止一个基于旧 ACTIVE 集合的
             * rebuild 覆盖已完成的并发新增；调用方收到稳定并发错误后应重新查询 ACTIVE 全集再重建。
             */
            if (snapshot.revision != expectedRevision) {
                throw new KnowledgeIndexException(
                        "KNOWLEDGE_INDEX_CONCURRENT_UPDATE", "knowledge index changed during rebuild");
            }
            RuntimeSnapshot candidate = buildSnapshot(
                    configuredModel, embedded.stream().sorted(ENTRY_ORDER).toList(), expectedRevision + 1);
            persistAndPublish(candidate);
        } finally {
            writerLock.unlock();
        }
    }

    private RuntimeSnapshot incrementalCandidate(List<KnowledgeIndexEntryFile> embedded) {
        RuntimeSnapshot current = snapshot;
        requireIncrementalModelCompatibility(current);
        LinkedHashMap<String, KnowledgeIndexEntryFile> combined = new LinkedHashMap<>();
        current.entries.forEach(entry -> combined.put(entry.embeddingId(), entry));
        for (KnowledgeIndexEntryFile entry : embedded) {
            if (combined.putIfAbsent(entry.embeddingId(), entry) != null) {
                throw new KnowledgeIndexException(
                        "KNOWLEDGE_INDEX_DUPLICATE_ID", "knowledge index contains a duplicate embedding id");
            }
        }
        return buildSnapshot(
                configuredModel,
                combined.values().stream().sorted(ENTRY_ORDER).toList(),
                current.revision + 1);
    }

    private void requireWriterReady() {
        if (rebuildRequired) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_REBUILD_REQUIRED",
                    "knowledge index must be rebuilt from active documents before another write");
        }
    }

    private List<KnowledgeIndexEntryFile> embedChunks(
            List<KnowledgeChunk> chunks, boolean requireChunkId) {
        Objects.requireNonNull(chunks, "chunks");
        List<KnowledgeChunk> stable = chunks.stream()
                .map(chunk -> validateChunk(chunk, requireChunkId))
                .sorted(Comparator.comparing(KnowledgeChunk::documentId)
                        .thenComparingInt(KnowledgeChunk::chunkIndex)
                        .thenComparing(KnowledgeChunk::embeddingId))
                .toList();
        if (stable.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segments = stable.stream()
                .map(chunk -> TextSegment.from(chunk.content()))
                .toList();
        final List<Embedding> embeddings;
        try {
            embeddings = embeddingModel.embedAll(segments).content();
        } catch (RuntimeException exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_EMBEDDING_FAILED", "knowledge embeddings could not be generated", exception);
        }
        if (embeddings == null || embeddings.size() != stable.size()) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_EMBEDDING_FAILED", "knowledge embedding response size is invalid");
        }
        int dimension = embeddings.getFirst().dimension();
        List<KnowledgeIndexEntryFile> entries = new ArrayList<>(stable.size());
        for (int index = 0; index < stable.size(); index++) {
            KnowledgeChunk chunk = stable.get(index);
            Embedding embedding = embeddings.get(index);
            if (embedding == null || embedding.dimension() <= 0 || embedding.dimension() != dimension) {
                throw dimensionMismatch();
            }
            entries.add(new KnowledgeIndexEntryFile(
                    chunk.embeddingId(), chunk.documentId(), chunk.id(), chunk.chunkIndex(),
                    chunk.content(), embedding.vector().clone()));
        }
        return List.copyOf(entries);
    }

    private Embedding embedQuery(String text) {
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            if (embedding == null || embedding.dimension() <= 0) {
                throw dimensionMismatch();
            }
            return embedding;
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_EMBEDDING_FAILED", "knowledge query embedding could not be generated", exception);
        }
    }

    private KnowledgeChunk validateChunk(KnowledgeChunk chunk, boolean requireChunkId) {
        Objects.requireNonNull(chunk, "chunk");
        if (requireChunkId && (chunk.id() == null || chunk.id() <= 0)) {
            throw new IllegalArgumentException("knowledge chunk id must be positive");
        }
        String expectedId = "knowledge:" + chunk.documentId() + ":chunk:" + chunk.chunkIndex();
        if (!expectedId.equals(chunk.embeddingId()) || chunk.embeddingId().length() > 128) {
            throw new IllegalArgumentException("knowledge chunk embedding id is invalid");
        }
        return chunk;
    }

    private List<KnowledgeIndexEntryFile> bindPersistedChunks(
            PreparedBatch prepared, List<KnowledgeChunk> persistedChunks) {
        if (!configuredModel.equals(prepared.embeddingModel)) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_MODEL_MISMATCH", "prepared embeddings use another model");
        }
        Map<String, KnowledgeChunk> persistedById = new HashMap<>();
        for (KnowledgeChunk chunk : persistedChunks) {
            validateChunk(chunk, true);
            if (persistedById.put(chunk.embeddingId(), chunk) != null) {
                throw new IllegalArgumentException("persisted chunks contain duplicate embedding ids");
            }
        }
        if (persistedById.size() != prepared.entries.size()) {
            throw new IllegalArgumentException("persisted chunks do not match prepared embeddings");
        }
        List<KnowledgeIndexEntryFile> bound = new ArrayList<>(prepared.entries.size());
        for (KnowledgeIndexEntryFile entry : prepared.entries) {
            KnowledgeChunk chunk = persistedById.get(entry.embeddingId());
            if (chunk == null || !entry.documentId().equals(chunk.documentId())
                    || entry.chunkIndex() != chunk.chunkIndex() || !entry.content().equals(chunk.content())) {
                throw new IllegalArgumentException("persisted chunks do not match prepared embeddings");
            }
            bound.add(new KnowledgeIndexEntryFile(
                    entry.embeddingId(), entry.documentId(), chunk.id(), entry.chunkIndex(),
                    entry.content(), entry.vector().clone()));
        }
        return List.copyOf(bound);
    }

    private void requireIncrementalModelCompatibility(RuntimeSnapshot current) {
        /*
         * 模型标识和向量维度共同定义向量空间。模型不同却写入同一文件会让相似度失去意义，
         * 因此只允许用当前模型执行全量 rebuild，绝不把新模型向量增量混入旧快照。
         */
        if (!current.entries.isEmpty() && !configuredModel.equals(current.embeddingModel)) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_MODEL_MISMATCH",
                    "knowledge index model differs from the configured embedding model; rebuild is required");
        }
        int reportedDimension = embeddingModel.dimension();
        if (!current.entries.isEmpty() && reportedDimension > 0
                && reportedDimension != current.vectorDimension) {
            throw dimensionMismatch();
        }
    }

    private RuntimeSnapshot buildSnapshot(
            String model, List<KnowledgeIndexEntryFile> entries, long revision) {
        List<KnowledgeIndexEntryFile> stable = entries.stream().sorted(ENTRY_ORDER).toList();
        Map<String, KnowledgeIndexEntryFile> unique = new HashMap<>();
        int dimension = stable.isEmpty() ? 0 : stable.getFirst().vector().length;
        for (KnowledgeIndexEntryFile entry : stable) {
            validateEntry(entry, dimension);
            if (unique.put(entry.embeddingId(), entry) != null) {
                throw new KnowledgeIndexException(
                        "KNOWLEDGE_INDEX_FORMAT_INVALID", "knowledge index contains duplicate embedding ids");
            }
        }
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        if (!stable.isEmpty()) {
            store.addAll(
                    stable.stream().map(KnowledgeIndexEntryFile::embeddingId).toList(),
                    stable.stream().map(entry -> Embedding.from(entry.vector().clone())).toList(),
                    stable.stream().map(entry -> TextSegment.from(entry.content())).toList());
        }
        return new RuntimeSnapshot(model, dimension, stable, Map.copyOf(unique), store, revision);
    }

    private RuntimeSnapshot loadFormalSnapshot() {
        Path formal = properties.indexFile();
        if (!Files.exists(formal)) {
            return buildSnapshot(configuredModel, List.of(), 0);
        }
        try {
            KnowledgeIndexSnapshotFile file = codec.decode(Files.readAllBytes(formal));
            if (file == null || file.formatVersion() != FORMAT_VERSION
                    || file.embeddingModel() == null || file.embeddingModel().isBlank()
                    || file.entries() == null || file.vectorDimension() < 0) {
                throw incompatibleFormat();
            }
            RuntimeSnapshot loaded = buildSnapshot(file.embeddingModel(), file.entries(), 0);
            if (loaded.vectorDimension != file.vectorDimension()) {
                throw incompatibleFormat();
            }
            return loaded;
        } catch (KnowledgeIndexException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_LOAD_FAILED", "knowledge index could not be loaded", exception);
        }
    }

    private void persistAndPublish(RuntimeSnapshot candidate) {
        persist(candidate);
        /*
         * 只有正式文件原子替换成功后才交换不可变内存快照。序列化、.tmp 写入或移动任一步骤失败，
         * 都会在到达这里前抛出，因此 search 始终继续读取上一份完整快照。
         */
        snapshot = candidate;
        rebuildRequired = false;
    }

    private void persist(RuntimeSnapshot candidate) {
        KnowledgeIndexSnapshotFile file = new KnowledgeIndexSnapshotFile(
                FORMAT_VERSION, candidate.embeddingModel, candidate.vectorDimension, candidate.entries);
        byte[] bytes = codec.encode(file);
        Path formal = properties.indexFile();
        Path temporary = properties.temporaryFile();
        try {
            Path parent = formal.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            /*
             * 正式文件代表可读取的完整快照，所以先覆盖同目录固定 .tmp，并在关闭、flush、force 后
             * 仅用 ATOMIC_MOVE 替换正式文件。文件系统不支持时必须失败，不能退化为复制或直接覆盖。
             */
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            mover.move(temporary, formal);
        } catch (IOException exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_PERSIST_FAILED", "knowledge index could not be atomically persisted", exception);
        }
    }

    private KnowledgeMatch toKnowledgeMatch(
            EmbeddingMatch<TextSegment> match, Map<String, KnowledgeIndexEntryFile> entries) {
        KnowledgeIndexEntryFile entry = entries.get(match.embeddingId());
        if (entry == null) {
            throw incompatibleFormat();
        }
        return new KnowledgeMatch(entry.embeddingId(), entry.documentId(), entry.chunkId(),
                entry.chunkIndex(), entry.content(), match.score());
    }

    private void validateEntry(KnowledgeIndexEntryFile entry, int expectedDimension) {
        if (entry == null || entry.documentId() == null || entry.documentId() <= 0
                || entry.chunkId() == null || entry.chunkId() <= 0 || entry.chunkIndex() < 0
                || entry.content() == null || entry.content().isBlank()
                || entry.vector() == null || entry.vector().length == 0
                || entry.vector().length != expectedDimension) {
            throw incompatibleFormat();
        }
        String expectedId = "knowledge:" + entry.documentId() + ":chunk:" + entry.chunkIndex();
        if (!expectedId.equals(entry.embeddingId()) || entry.embeddingId().length() > 128) {
            throw incompatibleFormat();
        }
        for (float value : entry.vector()) {
            if (!Float.isFinite(value)) {
                throw incompatibleFormat();
            }
        }
    }

    private KnowledgeIndexException incompatibleFormat() {
        return new KnowledgeIndexException(
                "KNOWLEDGE_INDEX_FORMAT_INVALID", "knowledge index format is invalid or incompatible");
    }

    private KnowledgeIndexException dimensionMismatch() {
        return new KnowledgeIndexException(
                "KNOWLEDGE_INDEX_DIMENSION_MISMATCH", "knowledge index vector dimension is incompatible");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private record RuntimeSnapshot(
            String embeddingModel,
            int vectorDimension,
            List<KnowledgeIndexEntryFile> entries,
            Map<String, KnowledgeIndexEntryFile> byEmbeddingId,
            InMemoryEmbeddingStore<TextSegment> store,
            long revision
    ) {
    }

    private record PreparedBatch(
            LangChain4jKnowledgeIndex owner,
            String embeddingModel,
            List<KnowledgeIndexEntryFile> entries
    ) implements PreparedKnowledgeIndexBatch {
    }
}
