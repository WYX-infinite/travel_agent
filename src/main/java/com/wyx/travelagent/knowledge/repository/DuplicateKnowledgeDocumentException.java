package com.wyx.travelagent.knowledge.repository;

/**
 * 表示数据库 SHA-256 唯一约束拒绝了并发重复文档。
 * <p>
 * Mapper 适配器保留底层原因供诊断，入库服务把它转换为稳定业务错误；异常消息不输出 SQL、表名或堆栈。
 */
public final class DuplicateKnowledgeDocumentException extends RuntimeException {

    /**
     * 创建并发重复文档异常。
     *
     * @param cause 数据库唯一约束异常，仅供服务端诊断
     */
    public DuplicateKnowledgeDocumentException(Throwable cause) {
        super("knowledge document content already exists", cause);
    }
}
