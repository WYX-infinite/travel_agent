package com.wyx.travelagent.knowledge.retrieval;

import java.util.List;

/**
 * 定义从数据库批量读取目标城市 ACTIVE 切片元数据的只读端口。
 * <p>
 * 实现必须以单次批量查询返回城市事实快照，禁止按向量命中逐条回查造成 N+1。
 */
@FunctionalInterface
public interface KnowledgeRetrievalMetadataSource {
    /**
     * 查询指定城市当前 ACTIVE 文档的全部切片和来源信息。
     *
     * @param city 已去除首尾空白的精确城市名
     * @return 按文档与切片顺序稳定排列的元数据
     */
    List<KnowledgeRetrievalMetadata> findActiveByCity(String city);
}
