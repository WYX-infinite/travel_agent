package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.model.KnowledgeDocument;

import java.util.Objects;

/**
 * 表示任务八完成校验、落盘和 Tika 解析后的内部结果。
 * <p>
 * 它向 Controller 提供文档元数据，并把正文保留给后续任务九的切片流程；
 * Controller 不直接返回完整正文，本记录也不写数据库、不生成 Embedding。
 *
 * @param document      已安全保存且状态为 UPLOADED 的不可变文档元数据
 * @param extractedText Tika 清理后的有效正文
 */
public record UploadedKnowledgeDocument(
        KnowledgeDocument document,
        String extractedText
) {
    /**
     * 保证上传结果始终包含文档和有效正文。
     *
     * @throws NullPointerException     文档为空时抛出
     * @throws IllegalArgumentException 正文为空时抛出
     */
    public UploadedKnowledgeDocument {
        Objects.requireNonNull(document, "document");
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("extractedText must not be blank");
        }
    }
}
