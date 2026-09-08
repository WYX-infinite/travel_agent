package com.wyx.travelagent.knowledge.retrieval;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 经城市、状态、索引一致性和分数规则验证后的可追溯知识引用。
 * <p>
 * 来源字段全部取自数据库元数据；模型只能提交 {@code chunkId}，不能替换标题、URL 或正文。
 *
 * @param documentId 文档主键
 * @param chunkId 本次白名单使用的切片主键
 * @param chunkIndex 切片序号
 * @param title 标题
 * @param city 城市
 * @param publisher 发布者
 * @param sourceUrl 来源地址，仅作为数据且不会被访问
 * @param publishedAt 发布日期
 * @param documentType 文档类型
 * @param content 完整正文
 * @param score 相似度得分
 */
public record KnowledgeReference(
        Long documentId, Long chunkId, int chunkIndex, String title, String city, String publisher,
        URI sourceUrl, LocalDate publishedAt, KnowledgeDocumentType documentType,
        String content, double score) {
    /** 校验引用不可变值对象的完整性。 */
    public KnowledgeReference {
        if (documentId == null || documentId <= 0 || chunkId == null || chunkId <= 0 || chunkIndex < 0
                || !Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("knowledge reference is invalid");
        }
        title = requireText(title, "title");
        city = requireText(city, "city");
        publisher = requireText(publisher, "publisher");
        content = requireText(content, "content");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        String scheme = sourceUrl.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || sourceUrl.getHost() == null || sourceUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("sourceUrl must be an absolute HTTP(S) URL");
        }
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(documentType, "documentType");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
