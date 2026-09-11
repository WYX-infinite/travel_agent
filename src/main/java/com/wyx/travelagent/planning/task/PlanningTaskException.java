package com.wyx.travelagent.planning.task;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * 业务问题：把任务层预期失败限制为稳定错误码、固定安全消息和 HTTP 状态。
 * 输入不接收底层异常文本，输出供统一异常处理器使用；本异常不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四后端 API 与任务十五前端；不负责记录堆栈、执行补偿或判断资源所有权。
 */
public final class PlanningTaskException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    /**
     * 创建脱敏任务异常。
     * @param code 非空白稳定错误码
     * @param message 非空白固定安全消息，不得包含底层异常原文
     * @param status 非空 HTTP 状态
     * 构造只保存内存值，不访问外部资源，也不处理认证。
     */
    public PlanningTaskException(String code, String message, HttpStatus status) {
        super(requireText(message, "message"));
        this.code = requireText(code, "code");
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * @return 非空稳定错误码
     * 无输入和缺失语义，不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
     */
    public String code() { return code; }

    /**
     * @return 非空 HTTP 状态
     * 无输入和缺失语义，不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
     */
    public HttpStatus status() { return status; }

    /**
     * 创建统一存储失败异常。
     * @return HTTP 500 的 PLANNING_TASK_STORAGE_FAILED
     * 不接收底层异常，因此不会泄露 SQL、路径或堆栈；不访问外部资源，也不处理认证。
     */
    public static PlanningTaskException storageFailure() {
        return new PlanningTaskException("PLANNING_TASK_STORAGE_FAILED",
                "planning task storage failed", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field);
        return value;
    }
}
