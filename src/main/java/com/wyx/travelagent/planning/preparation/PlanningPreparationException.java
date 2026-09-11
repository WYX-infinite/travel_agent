package com.wyx.travelagent.planning.preparation;

/**
 * 业务问题：用稳定代码表达准备阶段失败，避免底层供应商消息进入任务或 Trace。
 * 输入为稳定错误码，输出为不含 cause 的领域异常；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三仅用它控制准备阶段失败；本类型不负责重试、记录 Trace 或保存原始响应。
 */
public final class PlanningPreparationException extends RuntimeException {
    private final String code;

    /**
     * @param code 非空白稳定错误码
     * @throws IllegalArgumentException 当错误码为空白
     * 构造不访问外部资源，也不处理认证；异常不保留底层 cause。
     */
    public PlanningPreparationException(String code) {
        super(requireCode(code));
        this.code = code;
    }

    /** @return 稳定错误码；本方法不访问外部资源，也不处理认证。 */
    public String code() {
        return code;
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("code");
        return value;
    }
}
