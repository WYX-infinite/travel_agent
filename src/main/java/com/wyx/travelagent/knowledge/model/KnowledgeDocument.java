package com.wyx.travelagent.knowledge.model;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 知识文档的不可变元数据快照，对应 {@code knowledge_document} 表已经确定的业务字段。
 * <p>
 * 上传服务以校验后的表单、真实文件信息和上传用户构造该记录，Controller 只选择安全字段返回。
 * {@code id} 在任务八尚未入库时允许为空；数据库 ID、重复哈希唯一性、状态迁移和向量索引均由任务九负责。
 *
 * @param id               数据库主键；任务八尚未持久化，因此可以为空
 * @param title            管理员填写的文档标题
 * @param city             文档适用城市
 * @param publisher        文档发布机构
 * @param sourceUrl        仅作为元数据保存的 HTTP/HTTPS 来源地址，构造过程不会访问该地址
 * @param publishedAt      来源文档发布日期
 * @param documentType     文档的业务类别
 * @param originalFilename 经路径穿越校验后保留用于展示的原始文件名
 * @param storedFilename   服务端生成且仅含白名单扩展名的实际存储文件名
 * @param contentType      Apache Tika 根据实际字节检测到的 Content-Type
 * @param fileSize         实际从上传流读取的字节数
 * @param sha256           根据实际文件字节计算的小写十六进制 SHA-256
 * @param status           当前文档状态；任务八成功上传时固定为 UPLOADED
 * @param failureReason    脱敏后的失败原因；成功上传时为空
 * @param uploadedBy       从 CurrentUserContext 获取的上传用户 ID
 * @param createdAt        元数据创建时间
 * @param updatedAt        元数据最后更新时间
 */
public record KnowledgeDocument(
        Long id,
        String title,
        String city,
        String publisher,
        URI sourceUrl,
        LocalDate publishedAt,
        KnowledgeDocumentType documentType,
        String originalFilename,
        String storedFilename,
        String contentType,
        long fileSize,
        String sha256,
        KnowledgeDocumentStatus status,
        String failureReason,
        Integer uploadedBy,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * 校验不可变快照中的关键不变量，避免后续代码得到可随意修改或含糊的业务状态。
     *
     * @throws NullPointerException     必填引用字段为空时抛出
     * @throws IllegalArgumentException 文本为空、大小非法、哈希格式错误或用户 ID 非正数时抛出
     */
    public KnowledgeDocument {
        requireText(title, "title");
        requireText(city, "city");
        requireText(publisher, "publisher");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(documentType, "documentType");
        requireText(originalFilename, "originalFilename");
        requireText(storedFilename, "storedFilename");
        requireText(contentType, "contentType");
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        if (sha256 == null || !SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be a lowercase 64-character digest");
        }
        Objects.requireNonNull(status, "status");
        if (uploadedBy == null || uploadedBy <= 0) {
            throw new IllegalArgumentException("uploadedBy must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
