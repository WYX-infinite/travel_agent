package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;

import java.util.List;
import java.util.Optional;

/**
 * 定义任务九入库服务需要的短数据库阶段，不包含远程 Embedding 或磁盘索引操作。
 * <p>
 * 输入输出均使用不可变领域模型；生产适配器通过 Mapper 和 TransactionTemplate 实现生成键及切片批量事务。
 * Controller 不直接依赖本端口，任务十也不会通过它执行检索过滤。
 */
public interface KnowledgeIngestionPersistence {

    /**
     * 按 SHA-256 提前查询重复文档。
     *
     * @param sha256 小写 64 位摘要
     * @return 已存在文档；不存在时为空
     */
    Optional<KnowledgeDocument> findBySha256(String sha256);

    /**
     * 插入 UPLOADED 元数据并返回数据库生成的 Long 主键。
     *
     * @param document 尚无 ID 的 UPLOADED 文档
     * @return 带数据库 ID 的不可变文档
     * @throws DuplicateKnowledgeDocumentException 并发请求命中数据库 SHA-256 唯一约束时抛出
     */
    KnowledgeDocument insertUploaded(KnowledgeDocument document);

    /**
     * 使用“当前状态 + 目标状态”条件更新阻止非法或并发迁移。
     *
     * @param documentId 文档 BIGINT 主键
     * @param expected   WHERE 条件要求的当前状态
     * @param target     目标状态
     * @return 仅当恰好一行完成迁移时为 true
     */
    boolean transition(Long documentId, KnowledgeDocumentStatus expected, KnowledgeDocumentStatus target);

    /**
     * 在一个短数据库事务中插入全部切片并取得 Long 主键。
     *
     * @param chunks 已有稳定 embeddingId、尚无数据库 ID 的完整切片
     * @return 与输入一一对应且带生成 ID 的切片
     */
    List<KnowledgeChunk> insertAllChunks(List<KnowledgeChunk> chunks);

    /**
     * 仅将仍处于 INDEXING 的文档标记 FAILED，并保存脱敏原因。
     *
     * @param documentId 文档 BIGINT 主键
     * @param reason     不含正文、向量、SQL、密钥或路径的稳定原因
     * @return 条件更新恰好影响一行时为 true
     */
    boolean markFailed(Long documentId, String reason);

    /**
     * 查询数据库当前全部 ACTIVE 文档的完整切片。
     *
     * @return 按 documentId、chunkIndex 稳定排序的切片
     */
    List<KnowledgeChunk> findAllActiveChunks();

    /**
     * 删除指定文档切片，供失败补偿或后续管理任务使用。
     *
     * @param documentId 文档 BIGINT 主键
     */
    void deleteChunks(Long documentId);
}
