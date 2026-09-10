package com.wyx.travelagent.planning.context;

/**
 * 业务问题：在必需上下文本身超限时以稳定代码阻止模型调用。
 * 输入为内部预算失败，输出为不含 Prompt 或本机信息的业务异常；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一数据可能触发本错误，任务十二在生成前抛出，后续编排负责映射状态；本类型不负责重试、自动修正或记录原始内容。
 */
public final class ContextBudgetExceededException extends IllegalArgumentException {
    /** 稳定上下文超限错误码。 */
    public static final String ERROR_CODE = "CONTEXT_BUDGET_EXCEEDED";

    /**
     * 创建脱敏上下文超限异常。
     * 无输入，消息仅包含稳定错误码；构造不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public ContextBudgetExceededException() {
        super(ERROR_CODE);
    }

    /**
     * 返回稳定业务错误码。
     *
     * @return 固定 CONTEXT_BUDGET_EXCEEDED
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public String code() {
        return ERROR_CODE;
    }
}
