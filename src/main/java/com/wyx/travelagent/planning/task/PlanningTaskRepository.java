package com.wyx.travelagent.planning.task;

import java.time.Instant;
import java.util.Optional;

/**
 * 业务问题：隔离异步任务状态机与 planning_task 的并发、JSON 和 SQL 实现。
 * 输入始终带可信用户或任务编号，输出为用户隔离任务或条件更新结果；生产实现访问数据库，但接口本身不访问模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只服务任务十四持久化与重放边界；不负责 Controller、任务十三模型编排、SSE 发送或任务十五页面。
 */
public interface PlanningTaskRepository {
    /**
     * 在同一事务中锁定用户、检查活动任务并插入 PENDING 记录。
     * @param draft 非空冻结创建命令
     * @return 创建成功的任务；已有 PENDING/RUNNING 时为空
     * 实现可访问数据库但不调用模型、网络或线程池，认证由上层入口负责。
     */
    Optional<PlanningTask> createPendingIfNoActive(PendingTask draft);

    /** @param taskId 正任务编号 @param userId 正可信用户编号 @return 属于该用户的任务，否则为空；实现仅查询数据库，认证由上层入口负责。 */
    Optional<PlanningTask> findOwned(long taskId, int userId);

    /** @param taskId 正任务编号 @return 存在时的任务，否则为空；仅后台/SSE 已完成所有权校验后使用，实现仅查询数据库，不处理认证。 */
    default Optional<PlanningTask> findById(long taskId) { return Optional.empty(); }

    /** @param planId 正行程编号 @param userId 正可信用户编号 @return 该用户最近关联任务，否则为空；实现仅查询数据库，认证由上层入口负责。 */
    Optional<PlanningTask> findLatestByPlanAndUser(long planId, int userId);

    /** @param taskId 正任务编号 @param updatedAt 非空更新时间 @return 仅 PENDING 条件更新成功时为 true；实现仅写数据库，不处理认证。 */
    boolean markRunning(long taskId, Instant updatedAt);

    /** @param taskId 正任务编号 @param step 非空白稳定步骤 @param updatedAt 非空更新时间 @return 仅 RUNNING 更新成功时为 true；实现仅写数据库，不处理认证。 */
    boolean updateStep(long taskId, String step, Instant updatedAt);

    /**
     * 从 RUNNING 条件写入终态。
     * @param taskId 正任务编号
     * @param status 非空终态
     * @param planId COMPLETED 必填，其他状态可为空
     * @param code FAILED 必填稳定码，其他状态为空
     * @param reason FAILED 必填固定说明，其他状态为空
     * @param updatedAt 非空更新时间
     * @return 恰好一行更新成功时为 true
     * 实现只写数据库，不访问模型、缓存、文件、线程池或网络，认证由上层入口负责。
     */
    boolean finish(long taskId, PlanningTaskStatus status, Long planId,
                   String code, String reason, Instant updatedAt);

    /** @param taskId 正任务编号 @return 仅删除仍为 PENDING 的记录时为 true；实现只写数据库，不处理认证。 */
    boolean deletePending(long taskId);

    /**
     * 把遗留 RUNNING 批量失败化。
     * @param updatedAt 非空恢复时刻
     * @param code 非空稳定 SERVER_RESTARTED
     * @param reason 非空固定脱敏说明
     * @return 实际更新数量
     * 实现只写数据库，不访问模型、缓存、文件、线程池或网络，不处理认证。
     */
    int failRunningTasks(Instant updatedAt, String code, String reason);
}
