package com.wyx.travelagent.trace;

/**
 * 业务问题：用稳定值标识同步规划的关键阶段，使 Trace 可查询且不依赖自由文本。
 * 输入为编排当前阶段，输出为可持久化枚举值；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三只定义同步步骤；本类型不负责执行步骤、记录耗时或实现任务十四异步状态机。
 */
public enum PlanningStep {
    /** 需求合并与完整性校验。 */
    REQUIREMENT,
    /** 仅执行按城市隔离的知识检索，不表示工具已经运行。 */
    KNOWLEDGE_RETRIEVAL,
    /** 仅执行经过白名单校验的旅行工具，不表示模型已经生成行程。 */
    TOOL_EXECUTION,
    /** 知识、工具与可信事实准备。 */
    PREPARATION,
    /** 初稿结构化生成。 */
    GENERATION,
    /** 当前版本的全量 Java 校验与指标计算。 */
    VALIDATION,
    /** 基于上一可解析版本的单次修正。 */
    REPAIR,
    /** 同步规划最终状态确认。 */
    COMPLETION
}
