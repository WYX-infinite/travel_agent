package com.wyx.travelagent.knowledge.index;

import java.util.Objects;

/**
 * 表示本地知识索引生成、校验、加载或原子保存失败。
 * <p>
 * 对外只提供稳定错误码和脱敏消息，底层原因仅供服务端诊断；异常不会携带向量、完整正文、密钥或本机路径。
 * 数据库状态和 HTTP 响应由入库服务及统一异常处理器负责。
 */
public final class KnowledgeIndexException extends RuntimeException {
    private final String code;

    /**
     * 创建不携带底层原因的索引异常。
     *
     * @param code    稳定业务错误码
     * @param message 不含敏感信息的简短说明
     */
    public KnowledgeIndexException(String code, String message) {
        this(code, message, null);
    }

    /**
     * 创建保留服务端根因的索引异常。
     *
     * @param code    稳定业务错误码
     * @param message 不含敏感信息的简短说明
     * @param cause   仅供诊断的底层异常
     */
    public KnowledgeIndexException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * 返回稳定业务错误码。
     *
     * @return 不包含底层实现细节的错误码
     */
    public String code() {
        return code;
    }
}
