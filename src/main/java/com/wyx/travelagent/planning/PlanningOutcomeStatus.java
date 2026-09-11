package com.wyx.travelagent.planning;

/**
 * 业务问题：表示同步规划编排的最终语义，供后续异步任务层映射而不提前实现任务十四。
 * 输入是同步编排事实，输出为稳定结果状态；本枚举不访问外部资源，也不处理认证。
 * 任务十三用它区分成功、待调整、预算超限与失败；本类型不负责异步任务流转或持久化。
 */
public enum PlanningOutcomeStatus {
    /** 已有不含 ERROR 的持久化版本；WARNING 允许保留。 */
    COMPLETED,
    /** 初稿及最多两次修正仍含 ERROR，需要用户调整约束。 */
    NEEDS_ADJUSTMENT,
    /** 必需结构化上下文在模型调用前超过保守预算。 */
    CONTEXT_BUDGET_EXCEEDED,
    /** 数据源、模型或持久化流程以稳定错误码失败。 */
    FAILED
}
