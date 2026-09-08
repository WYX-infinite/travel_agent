package com.wyx.travelagent.knowledge.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 映射目标城市 ACTIVE 文档及其切片的一次性只读联表查询。 */
@Mapper
interface KnowledgeRetrievalMapper {
    /**
     * @param city 精确城市名
     * @return 按文档、切片序号、切片主键稳定排序的全部 ACTIVE 城市切片
     */
    @Select("""
            SELECT d.id AS documentId, c.id AS chunkId, c.chunk_index AS chunkIndex,
                   c.embedding_id AS embeddingId, c.content, d.title, d.city, d.publisher,
                   d.source_url AS sourceUrl, d.published_at AS publishedAt,
                   d.document_type AS documentType
            FROM knowledge_chunk c
            JOIN knowledge_document d ON d.id = c.document_id
            WHERE d.status = 'ACTIVE' AND d.city = #{city}
            ORDER BY c.document_id, c.chunk_index, c.id
            """)
    List<KnowledgeRetrievalRow> findActiveByCity(String city);
}
