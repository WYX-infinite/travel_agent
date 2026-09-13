package com.wyx.travelagent.knowledge.admin;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/** 知识库后台管理可安全映射到统一 API 的稳定业务异常。 */
public final class KnowledgeAdministrationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public KnowledgeAdministrationException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public KnowledgeAdministrationException(
            String code, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.status = Objects.requireNonNull(status, "status");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
