package com.wyx.travelagent.trace;

/**
 * 业务问题：把数据库单调主键与脱敏 Trace 事件绑定，供任务十四 SSE 断线重放。
 * 输入来自严格仓储映射，输出为不可变重放元素；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只服务任务十四事件游标；不负责写 Trace、用户所有权、SSE 发送或保存原始内容。
 * @param eventId 正且单调递增的 planning_trace 主键
 * @param event 非空脱敏事件
 */
public record PersistedPlanningTrace(long eventId, PlanningTraceEvent event) {
    /** 创建重放元素；只校验内存值，不访问外部资源或认证信息。 */
    public PersistedPlanningTrace {
        if (eventId <= 0) throw new IllegalArgumentException("eventId");
        java.util.Objects.requireNonNull(event, "event");
    }
}
