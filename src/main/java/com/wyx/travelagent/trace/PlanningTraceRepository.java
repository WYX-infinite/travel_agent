package com.wyx.travelagent.trace;

/**
 * 业务问题：持久化一条已经脱敏的 Trace 事件。
 * 实现可访问数据库，但不得读取模型、工具、缓存或认证上下文。
 * 输入为非空安全事件，输出为写入副作用；接口本身不访问外部资源，也不处理认证。任务十三只定义写入边界；本类型不负责脱敏、事务编排或查询。
 */
@FunctionalInterface
public interface PlanningTraceRepository {
    /**
     * 追加一条脱敏事件。
     *
     * @param event 非空且已通过字段白名单约束的事件
     * 实现可以写入数据库，但不得访问模型、工具、缓存或文件；认证由上层入口负责。
     */
    void append(PlanningTraceEvent event);
}
