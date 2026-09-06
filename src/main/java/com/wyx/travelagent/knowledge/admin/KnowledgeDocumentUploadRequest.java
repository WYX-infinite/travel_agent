package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;

import java.time.LocalDate;

/**
 * 汇集管理员上传时必须提供的来源元数据。
 * <p>
 * Controller 将 multipart 字段转换为该记录，上传服务负责统一校验并输出文档元数据；
 * 本记录不包含客户端用户 ID，不访问来源 URL，也不负责数据库持久化。
 *
 * @param title        文档标题
 * @param city         适用城市
 * @param publisher    发布机构
 * @param sourceUrl    仅作为元数据保存的 HTTP/HTTPS 来源 URL
 * @param publishedAt  文档发布日期
 * @param documentType 文档业务类别
 */
public record KnowledgeDocumentUploadRequest(
        String title,
        String city,
        String publisher,
        String sourceUrl,
        LocalDate publishedAt,
        KnowledgeDocumentType documentType
) {
}
