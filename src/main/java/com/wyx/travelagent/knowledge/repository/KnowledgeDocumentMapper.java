package com.wyx.travelagent.knowledge.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 映射知识文档插入、按哈希查询和条件状态迁移。
 */
@Mapper
interface KnowledgeDocumentMapper {

    @Insert("""
            INSERT INTO knowledge_document (
                title, city, publisher, source_url, published_at, document_type,
                original_filename, stored_filename, content_type, file_size, sha256,
                status, failure_reason, uploaded_by, created_at, updated_at
            ) VALUES (
                #{title}, #{city}, #{publisher}, #{sourceUrl}, #{publishedAt}, #{documentType},
                #{originalFilename}, #{storedFilename}, #{contentType}, #{fileSize}, #{sha256},
                #{status}, #{failureReason}, #{uploadedBy}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeDocumentRow row);

    @Select("""
            SELECT id, title, city, publisher, source_url AS sourceUrl,
                   published_at AS publishedAt, document_type AS documentType,
                   original_filename AS originalFilename, stored_filename AS storedFilename,
                   content_type AS contentType, file_size AS fileSize, sha256, status,
                   failure_reason AS failureReason, uploaded_by AS uploadedBy,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM knowledge_document
            WHERE sha256 = #{sha256}
            """)
    KnowledgeDocumentRow findBySha256(String sha256);

    @Update("""
            UPDATE knowledge_document
            SET status = #{target}, failure_reason = NULL, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{expected}
            """)
    int transition(
            @Param("id") Long id,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("updatedAt") Instant updatedAt);

    @Update("""
            UPDATE knowledge_document
            SET status = 'FAILED', failure_reason = #{reason}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = 'INDEXING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("reason") String reason,
            @Param("updatedAt") Instant updatedAt);
}
