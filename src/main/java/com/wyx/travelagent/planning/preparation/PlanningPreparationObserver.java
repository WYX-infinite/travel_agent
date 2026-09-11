package com.wyx.travelagent.planning.preparation;

import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceDetails;
import com.wyx.travelagent.trace.TraceStatus;

/**
 * 业务问题：观察事实准备内部真实发生的知识检索与工具执行，供任务十四持久化进度和 Trace。
 * 输入是稳定步骤、结果、耗时与白名单摘要，输出为空；接口自身不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只连接任务十三准备网关和任务十四可观测性；不改变执行结果、不保存原始响应，也不负责 SSE。
 */
public interface PlanningPreparationObserver {
    /**
     * 通知真实子阶段开始。
     * @param step 非空且仅应为 KNOWLEDGE_RETRIEVAL、TOOL_EXECUTION 或兼容 PREPARATION
     * 实现可以持久化任务进度，但不应调用模型或工具；认证由上层负责。
     */
    void started(PlanningStep step);

    /**
     * 通知真实子阶段结束。
     * @param step 非空稳定步骤
     * @param status 非空成功、降级或失败状态
     * @param durationMs 非负耗时毫秒
     * @param details 非空白名单详情，不含原始外部响应
     * 实现可以写 Trace 数据库，但不调用模型或工具；认证由上层负责。
     */
    void completed(PlanningStep step, TraceStatus status, long durationMs, PlanningTraceDetails details);

    /**
     * 通知真实子阶段以稳定业务码失败。
     * @param step 非空稳定步骤
     * @param durationMs 非负耗时毫秒
     * @param errorCode 非空稳定错误码，不得包含异常原文
     * 实现可以写 Trace 数据库，但不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    void failed(PlanningStep step, long durationMs, String errorCode);

    /**
     * 返回不产生副作用的观察器。
     * @return 非空观察器
     * 无输入与缺失语义，不访问外部资源，也不处理认证。
     */
    static PlanningPreparationObserver none() {
        return new PlanningPreparationObserver() {
            @Override public void started(PlanningStep step) { }
            @Override public void completed(PlanningStep step, TraceStatus status, long durationMs,
                                            PlanningTraceDetails details) { }
            @Override public void failed(PlanningStep step, long durationMs, String errorCode) { }
        };
    }
}
