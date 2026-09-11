package com.wyx.travelagent.planning.api;

/**
 * 业务问题：按任务十四冻结协议公开一条可审计的活动证据，而不泄漏内部知识实体。
 * 输入应来自已验证且允许展示的引用元数据，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 当前行程领域模型未保存完整活动引用时，上层返回空列表；本类型不负责检索文档、补写来源或生成引用内容。
 *
 * @param type 可为空稳定证据类型
 * @param documentTitle 可为空文档标题
 * @param publisher 可为空发布机构
 * @param sourceUrl 可为空允许展示的来源地址
 * @param chunkId 可为空正知识切片编号
 * @param quote 可为空与活动直接相关的短引用
 */
public record PlanEvidenceResponse(
        String type, String documentTitle, String publisher, String sourceUrl, Long chunkId, String quote) {
    /** 创建证据响应；只校验内存主键，不访问外部资源或处理认证。 */
    public PlanEvidenceResponse {
        if (chunkId != null && chunkId <= 0) throw new IllegalArgumentException("INVALID_EVIDENCE_CHUNK_ID");
    }
}
