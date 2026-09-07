package com.wyx.travelagent.knowledge.ingest;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * 表示知识文档数据库、Embedding 与磁盘索引生命周期中的安全业务错误。
 * <p>
 * 输出稳定错误码、HTTP 状态和脱敏消息；GlobalExceptionHandler 负责统一响应，底层原因不向客户端泄漏。
 */
public final class KnowledgeIngestionException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    /**
     * 创建入库业务异常。
     *
     * @param code    稳定错误码
     * @param message 可安全返回的简短消息
     * @param status  HTTP 状态
     * @param cause   仅供服务端诊断的底层原因
     */
    public KnowledgeIngestionException(
            String code, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }

    /** @return 统一响应使用的 HTTP 状态 */
    public HttpStatus status() {
        return status;
    }
}
