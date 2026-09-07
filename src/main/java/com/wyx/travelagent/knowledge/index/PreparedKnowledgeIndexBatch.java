package com.wyx.travelagent.knowledge.index;

/**
 * 表示已经完成远程 Embedding、但尚未发布到磁盘和内存的不可变候选批次。
 * <p>
 * 入库服务把它作为不透明令牌跨过短数据库切片事务，再交回同一个索引实现绑定数据库 chunkId 并发布。
 * 接口故意不暴露向量或正文，避免 Controller、Mapper 或任务十检索逻辑依赖内部格式。
 */
public interface PreparedKnowledgeIndexBatch {
}
