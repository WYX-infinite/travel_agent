package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;

import java.util.List;
import java.util.Set;

/**
 * 定义任务九本地知识向量索引的最小业务端口。
 * <p>
 * 调用方提供已经持久化、带稳定标识的切片，索引负责生成向量、原子保存完整快照并提供基础相似度搜索。
 * 上传校验和数据库状态迁移不属于本接口职责；城市/ACTIVE 事实由数据库产生的切片白名单表达，
 * 索引只负责在该白名单内进行相似度排序，不解释城市或文档状态。
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
     * 只在数据库确认允许的切片集合内执行相似度排序。
     * <p>
     * 白名单过滤必须先于 {@code maxResults} 截断，避免其他城市或非 ACTIVE 文档占满候选窗口。
     * 空白名单直接返回空列表，不触发查询向量生成。
     *
     * @param query           非空查询文本
     * @param allowedChunkIds 本次数据库快照允许参与检索的正数切片主键
     * @param maxResults      最大返回数量，必须为正数
     * @return 白名单内按得分降序排列的稳定匹配
     * @throws KnowledgeIndexException 查询向量生成失败或索引不兼容时抛出
     */
    List<KnowledgeMatch> search(KnowledgeQuery query, Set<Long> allowedChunkIds, int maxResults);

    /**
     * 用数据库当前 ACTIVE 切片全集替换索引内容。
     *
     * @param activeChunks 数据库查询得到的完整 ACTIVE 切片集合，可以为空
     * @throws KnowledgeIndexException Embedding、并发版本校验、序列化或原子替换失败时抛出
     */
    void rebuild(List<KnowledgeChunk> activeChunks);
}
