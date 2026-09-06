package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;

/**
 * 管理员上传接口返回的安全摘要。
 * <p>
 * 输入来自已校验的 KnowledgeDocument，输出不包含完整正文或本机绝对路径；
 * 数据库 ID、切片和索引结果要到任务九完成后才能提供。
 *
 * @param title            文档标题
 * @param status           当前上传状态
 * @param originalFilename 经校验的原始文件名
 * @param storedFilename   服务端生成的存储文件名
 * @param contentType      Tika 检测到的实际媒体类型
 * @param fileSize         实际文件字节数
 * @param sha256           实际字节的 SHA-256 摘要
 */
public record KnowledgeDocumentUploadResponse(
        String title,
        KnowledgeDocumentStatus status,
        String originalFilename,
        String storedFilename,
        String contentType,
        long fileSize,
        String sha256
) {
}
