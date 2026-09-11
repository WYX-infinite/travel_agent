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

    /**
     * 按数据库主键升序读取游标之后的事件。
     * @param taskId 正任务编号
     * @param afterEventId 非负排他游标，零表示从头读取
     * @param limit 一至二百的批量上限
     * @return 非空、有序且不可修改的脱敏事件列表
     * 默认返回空以保持任务十三函数式 Fake 兼容；生产实现只查询数据库，认证由上层负责。
     */
    default java.util.List<PersistedPlanningTrace> findAfter(long taskId, long afterEventId, int limit) {
        return java.util.List.of();
    }
}
