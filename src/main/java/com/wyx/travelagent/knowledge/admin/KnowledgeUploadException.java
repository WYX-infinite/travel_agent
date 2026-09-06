package com.wyx.travelagent.knowledge.admin;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * 表示上传校验、解析或安全存储阶段可被统一响应处理的业务错误。
 * <p>
 * 服务输出稳定错误码、安全消息和 HTTP 状态，GlobalExceptionHandler 负责转换响应；
 * 异常消息不得包含本机路径、密钥、堆栈或完整正文。
 */
public final class KnowledgeUploadException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    /**
     * 创建上传业务异常。
     *
     * @param code    稳定错误码
     * @param message 可安全返回给客户端的简短消息
     * @param status  对应 HTTP 状态
     */
    public KnowledgeUploadException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    /**
     * 创建保留服务端根因的上传业务异常。
     *
     * @param code    稳定错误码
     * @param message 可安全返回给客户端的简短消息
     * @param status  对应 HTTP 状态
     * @param cause   仅供服务端诊断的原始异常
     */
    public KnowledgeUploadException(
            String code, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * 返回统一错误响应使用的稳定代码。
     *
     * @return 业务错误码
     */
    public String code() {
        return code;
    }

    /**
     * 返回统一错误响应使用的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus status() {
        return status;
    }
}
