package com.wyx.travelagent.knowledge.retrieval;

import java.util.Objects;

/**
 * 表示城市知识检索中可稳定识别、且不会泄漏底层数据的业务错误。
 * <p>
 * 对外消息只描述错误类别；数据库语句、城市数据、文件路径与用户提交的引用 ID
 * 只能保留在异常原因中供受控诊断，不能拼接到消息。
 */
public final class KnowledgeRetrievalException extends RuntimeException {
    private final String code;

    /**
     * 创建不携带底层原因的安全检索异常。
     *
     * @param code 稳定错误码
     * @param message 可安全返回的消息
     */
    public KnowledgeRetrievalException(String code, String message) {
        this(code, message, null);
    }

    /**
     * 创建保留诊断原因、但保持对外消息稳定的检索异常。
     *
     * @param code 稳定错误码
     * @param message 可安全返回的消息
     * @param cause 底层诊断原因
     */
    public KnowledgeRetrievalException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** @return 稳定业务错误码 */
    public String code() {
        return code;
    }
}
