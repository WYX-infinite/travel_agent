package com.wyx.travelagent.trace;

/**
 * 业务问题：区分 Trace 步骤成功、失败与可继续降级，避免用自然语言推断状态。
 * 输入为步骤执行事实，输出为稳定结果枚举；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三只使用本状态记录同步 Trace；本类型不负责决定业务流程或持久化事件。
 */
public enum TraceStatus {
    /** 步骤按预期完成。 */
    SUCCEEDED,
    /** 步骤以稳定错误码终止。 */
    FAILED,
    /** 步骤缺少可选数据或仍含校验问题，但流程按规则继续。 */
    DEGRADED
}
