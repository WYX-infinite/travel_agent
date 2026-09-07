package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;

import java.util.List;

/**
 * 定义任务九本地知识向量索引的最小业务端口。
 * <p>
 * 调用方提供已经持久化、带稳定标识的切片，索引负责生成向量、原子保存完整快照并提供基础相似度搜索。
 * 上传校验、数据库状态迁移以及任务十的城市过滤、阈值和引用校验不属于本接口职责。
 */
public interface KnowledgeIndex {

    /**
     * 把一组完整切片增量加入当前快照。
     *
     * @param chunks 已取得数据库 ID、稳定 embeddingId 且正文非空的切片
     * @throws KnowledgeIndexException 远程 Embedding、格式校验、临时文件写入或原子替换失败时抛出
     */
    void addAll(List<KnowledgeChunk> chunks);

    /**
     * 使用基础语义相似度搜索当前不可变快照。
     *
     * @param query      非空查询文本
     * @param maxResults 最大返回数量，必须为正数
     * @return 按得分降序排列的稳定匹配；空索引返回空列表
     * @throws KnowledgeIndexException 查询向量生成失败或当前模型与磁盘快照不兼容时抛出
     */
    List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults);

    /**
     * 用数据库当前 ACTIVE 切片全集替换索引内容。
     *
     * @param activeChunks 数据库查询得到的完整 ACTIVE 切片集合，可以为空
     * @throws KnowledgeIndexException Embedding、并发版本校验、序列化或原子替换失败时抛出
     */
    void rebuild(List<KnowledgeChunk> activeChunks);
}
