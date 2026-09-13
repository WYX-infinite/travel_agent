package com.wyx.travelagent.knowledge.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** 知识库后台管理的聚合、分页和条件写 SQL。 */
@Mapper
interface KnowledgeAdministrationMapper {
    @Select("""
            SELECT d.id, d.title, d.city, d.publisher, d.source_url AS sourceUrl,
                   d.published_at AS publishedAt, d.document_type AS documentType,
                   d.original_filename AS originalFilename, d.stored_filename AS storedFilename,
                   d.content_type AS contentType, d.file_size AS fileSize, d.sha256, d.status,
                   d.failure_reason AS failureReason, d.uploaded_by AS uploadedBy,
                   d.created_at AS createdAt, d.updated_at AS updatedAt,
                   COUNT(c.id) AS chunkCount
            FROM knowledge_document d
            LEFT JOIN knowledge_chunk c ON c.document_id = d.id
            GROUP BY d.id, d.title, d.city, d.publisher, d.source_url, d.published_at,
                     d.document_type, d.original_filename, d.stored_filename, d.content_type,
                     d.file_size, d.sha256, d.status, d.failure_reason, d.uploaded_by,
                     d.created_at, d.updated_at
            ORDER BY d.created_at DESC, d.id DESC
            """)
    List<KnowledgeAdministrationRow> findAllDocuments();

    @Select("""
            SELECT d.id, d.title, d.city, d.publisher, d.source_url AS sourceUrl,
                   d.published_at AS publishedAt, d.document_type AS documentType,
                   d.original_filename AS originalFilename, d.stored_filename AS storedFilename,
                   d.content_type AS contentType, d.file_size AS fileSize, d.sha256, d.status,
                   d.failure_reason AS failureReason, d.uploaded_by AS uploadedBy,
                   d.created_at AS createdAt, d.updated_at AS updatedAt,
                   COUNT(c.id) AS chunkCount
            FROM knowledge_document d
            LEFT JOIN knowledge_chunk c ON c.document_id = d.id
            WHERE d.id = #{id}
            GROUP BY d.id, d.title, d.city, d.publisher, d.source_url, d.published_at,
                     d.document_type, d.original_filename, d.stored_filename, d.content_type,
                     d.file_size, d.sha256, d.status, d.failure_reason, d.uploaded_by,
                     d.created_at, d.updated_at
            """)
    KnowledgeAdministrationRow findDocument(Long id);

    @Select("SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = #{documentId}")
    long countChunks(Long documentId);

    @Select("""
            SELECT id, document_id AS documentId, chunk_index AS chunkIndex,
                   content, embedding_id AS embeddingId, created_at AS createdAt
            FROM knowledge_chunk
            WHERE document_id = #{documentId}
            ORDER BY chunk_index, id
            LIMIT #{size} OFFSET #{offset}
            """)
    List<KnowledgeChunkRow> findChunks(
            @Param("documentId") Long documentId,
            @Param("offset") long offset,
            @Param("size") int size);

    @Update("""
            UPDATE knowledge_document
            SET status = #{target}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{expected}
            """)
    int updateStatus(
            @Param("id") Long id,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("updatedAt") Instant updatedAt);

    @Delete("""
            DELETE FROM knowledge_document
            WHERE id = #{id} AND status = #{expected}
            """)
    int deleteDocument(@Param("id") Long id, @Param("expected") String expected);

    @Select("SELECT COUNT(*) FROM knowledge_document WHERE status = 'ACTIVE'")
    long countActiveDocuments();
}
