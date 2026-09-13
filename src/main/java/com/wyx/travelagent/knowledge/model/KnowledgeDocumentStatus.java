package com.wyx.travelagent.knowledge.model;

/**
 * 描述知识文档在上传与后续索引流程中的状态名称。
 * <p>
 * 任务八的上传服务只产生 {@link #UPLOADED}；其余值先定义为任务九的模型边界，
 * 本类不实现状态迁移、数据库持久化或向量索引。
 */
public enum KnowledgeDocumentStatus {
    /** 文件已完成校验、解析并安全保存，尚未开始生成向量。 */
    UPLOADED,
    /** 任务九开始切片与向量构建后的处理中状态。 */
    INDEXING,
    /** 任务九确认数据库与正式索引一致后可参与检索的状态。 */
    ACTIVE,
    /** 管理员显式停用，数据库白名单和重建后的索引均不得包含其切片。 */
    DISABLED,
    /** 后续处理失败且保存了脱敏失败原因的状态。 */
    FAILED
}
