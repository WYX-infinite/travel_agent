package com.wyx.travelagent.planning.memory;

/**
 * 业务问题：向上层提供不泄露仓储细节的稳定偏好错误码。
 * 输入是稳定代码和可选底层异常，输出是运行时业务异常；本类型不访问外部资源或认证信息。
 * 任务十与任务十二不创建本异常；后续编排可映射错误，本类型不负责 HTTP 响应、日志或重试。
 */
public final class PreferenceMemoryException extends RuntimeException {
    private final String code;

    /**
     * 创建稳定偏好异常。
     * @param code 对外稳定错误码，不得为 {@code null}
     * @param cause 可选底层原因，仅保存在异常链中
     * 不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws NullPointerException 当 code 为 null
     */
    public PreferenceMemoryException(String code, Throwable cause) {
        super(code, cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    /**
     * 返回稳定错误码。
     * @return 非空稳定代码
     * 无参数且不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public String code() { return code; }
}
