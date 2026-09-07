package com.wyx.travelagent.knowledge.index;

/**
 * 返回任务九基础向量搜索命中的稳定数据库标识、正文和相似度。
 * <p>
 * 数据来自同一份不可变内存快照，可供后续任务十做城市过滤和引用校验；本记录不判断业务阈值，
 * 也不生成 {@code KnowledgeReference}。
 *
 * @param embeddingId 数据库切片与向量条目共享的稳定标识
 * @param documentId  知识文档 BIGINT 主键
 * @param chunkId     知识切片 BIGINT 主键
 * @param chunkIndex  文档内从 0 开始的稳定序号
 * @param content     完整切片正文
 * @param score       LangChain4j 返回的相似度得分
 */
public record KnowledgeMatch(
        String embeddingId,
        Long documentId,
        Long chunkId,
        int chunkIndex,
        String content,
        double score
) {
}
