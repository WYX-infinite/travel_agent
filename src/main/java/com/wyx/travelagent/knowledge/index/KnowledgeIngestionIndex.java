package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 为安全入库生命周期扩展 {@link KnowledgeIndex} 的两阶段写入边界。
 * <p>
 * {@link #prepare(List)} 只调用 EmbeddingModel，不访问数据库或磁盘；切片短事务取得 chunkId 后，
 * {@link #addPrepared(PreparedKnowledgeIndexBatch, List)} 可直接原子发布完整候选快照；安全上传生命周期使用
 * {@link #addPreparedAndActivate(PreparedKnowledgeIndexBatch, List, BooleanSupplier)}，让候选先落盘并在 ACTIVE
 * 条件更新成功后才对内存搜索可见。这样不会在数据库事务或索引写锁内等待不受控远程调用。
 * 普通检索仍只依赖最小 {@link KnowledgeIndex} 端口。
 */
public interface KnowledgeIngestionIndex extends KnowledgeIndex {

    /**
     * 在锁外为全部切片生成向量。
     *
     * @param chunks 已有 documentId 和稳定 embeddingId、尚可没有 chunkId 的完整切片集合
     * @return 不暴露向量内容的不透明候选批次
     * @throws KnowledgeIndexException 远程模型失败、返回数量或维度非法时抛出
     */
    PreparedKnowledgeIndexBatch prepare(List<KnowledgeChunk> chunks);

    /**
     * 将候选向量绑定到数据库已生成的 chunkId，并原子发布完整快照。
     *
     * @param batch           由同一个索引实例 prepare 得到的候选批次
     * @param persistedChunks 短数据库事务返回的完整持久化切片
     * @throws KnowledgeIndexException 批次不匹配、模型/维度冲突或磁盘原子替换失败时抛出
     */
    void addPrepared(PreparedKnowledgeIndexBatch batch, List<KnowledgeChunk> persistedChunks);

    /**
     * 原子写入候选文件后执行一次短条件 ACTIVE 更新，成功才交换内存搜索快照。
     * <p>
     * Embedding 必须已经由 {@link #prepare(List)} 在锁外完成；activation 只允许执行单条短数据库事务，
     * 不得包含远程模型或其他不受控工作。回调返回 false 时保留旧内存快照并阻止后续增量写，
     * 直到调用方使用数据库 ACTIVE 全集成功 rebuild。
     *
     * @param batch 已完成远程 Embedding 的候选批次
     * @param persistedChunks 已取得数据库 Long 主键的完整切片
     * @param activation INDEXING → ACTIVE 条件更新回调
     * @return ACTIVE 条件更新恰好成功时为 true
     * @throws KnowledgeIndexException 候选格式、模型、维度或原子文件写入失败时抛出
     */
    boolean addPreparedAndActivate(
            PreparedKnowledgeIndexBatch batch,
            List<KnowledgeChunk> persistedChunks,
            BooleanSupplier activation);
}
