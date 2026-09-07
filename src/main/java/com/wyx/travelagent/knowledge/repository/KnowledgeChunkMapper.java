package com.wyx.travelagent.knowledge.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 映射知识切片批次写入、补偿删除和 ACTIVE 文档切片查询。
 */
@Mapper
interface KnowledgeChunkMapper {

    @Insert("""
            INSERT INTO knowledge_chunk (
                document_id, chunk_index, content, embedding_id, created_at
            ) VALUES (
                #{documentId}, #{chunkIndex}, #{content}, #{embeddingId}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeChunkRow row);

    @Select("""
            SELECT c.id, c.document_id AS documentId, c.chunk_index AS chunkIndex,
                   c.content, c.embedding_id AS embeddingId, c.created_at AS createdAt
            FROM knowledge_chunk c
            JOIN knowledge_document d ON d.id = c.document_id
            WHERE d.status = 'ACTIVE'
            ORDER BY c.document_id, c.chunk_index
            """)
    List<KnowledgeChunkRow> findAllActive();

    @Delete("DELETE FROM knowledge_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(Long documentId);
}
