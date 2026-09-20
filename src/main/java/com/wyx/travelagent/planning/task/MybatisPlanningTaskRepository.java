package com.wyx.travelagent.planning.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 业务问题：使用用户行锁保证每个用户最多一条 PENDING/RUNNING 任务，并严格恢复冻结 JSON。
 * 输入是可信领域命令，输出为隔离后的任务或条件更新结果；本适配器只访问数据库，不调用模型、缓存、文件、线程池或网络，也不处理认证。
 * 它实现任务十四持久化；不负责 Controller、SSE、任务十三编排或建表迁移。
 */
@Repository
@Primary
public class MybatisPlanningTaskRepository implements PlanningTaskRepository {
    private final PlanningTaskMapper mapper;
    private final TransactionTemplate transactions;
    private final PlanningTaskPayloadCodec codec;

    /**
     * 创建任务仓储。
     * @param mapper 非空参数化 SQL mapper
     * @param transactions 非空事务模板
     * @param objectMapper 非空 JSON 编解码器
     * 构造只保存依赖，不访问外部资源，也不处理认证。
     */
    public MybatisPlanningTaskRepository(PlanningTaskMapper mapper, TransactionTemplate transactions,
                                         ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.codec = new PlanningTaskPayloadCodec(objectMapper);
    }

    /**
     * @param draft 非空冻结创建命令
     * @return 创建任务；用户已有活动任务时为空
     * 本方法只访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public Optional<PlanningTask> createPendingIfNoActive(PendingTask draft) {
        Objects.requireNonNull(draft, "draft");
        try {
            Optional<PlanningTask> result = transactions.execute(status -> {
                if (mapper.lockUser(draft.userId()) == null) throw PlanningTaskException.storageFailure();
                if (mapper.countActive(draft.userId()) > 0) return Optional.empty();
                PlanningTaskRow row = row(draft);
                if (mapper.insert(row) != 1 || row.getId() == null || row.getId() <= 0) {
                    throw PlanningTaskException.storageFailure();
                }
                return Optional.of(draft.toTask(row.getId()));
            });
            return Objects.requireNonNull(result, "transaction result");
        } catch (PlanningTaskException exception) { throw exception; }
        catch (RuntimeException exception) { throw PlanningTaskException.storageFailure(); }
    }

    /**
     * @param taskId 正任务编号
     * @param userId 正可信用户编号
     * @return 用户拥有时的任务，否则为空
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public Optional<PlanningTask> findOwned(long taskId, int userId) { return mapped(() -> mapper.findOwned(taskId, userId)); }
    /**
     * @param taskId 正任务编号
     * @return 存在时任务，否则为空
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public Optional<PlanningTask> findById(long taskId) { return mapped(() -> mapper.findById(taskId)); }
    /**
     * @param planId 正行程编号
     * @param userId 正可信用户编号
     * @return 最近关联任务，否则为空
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public Optional<PlanningTask> findLatestByPlanAndUser(long planId, int userId) { return mapped(() -> mapper.findLatestByPlanAndUser(planId, userId)); }
    /**
     * @param taskId 正任务编号
     * @param updatedAt 非空更新时间
     * @return PENDING 条件更新成功时为 true
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public boolean markRunning(long taskId, Instant updatedAt) { return write(() -> mapper.markRunning(taskId, updatedAt)) == 1; }
    /**
     * @param taskId 正任务编号
     * @param step 非空白稳定步骤
     * @param updatedAt 非空更新时间
     * @return RUNNING 条件更新成功时为 true
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public boolean updateStep(long taskId, String step, Instant updatedAt) { return write(() -> mapper.updateStep(taskId, step, updatedAt)) == 1; }

    /**
     * @param taskId 正任务编号
     * @param status 非空终态
     * @param planId 可为空；COMPLETED 时应为正行程编号
     * @param code 可为空；FAILED 时为稳定错误码
     * @param reason 可为空；FAILED 时为固定安全说明
     * @param updatedAt 非空更新时间
     * @return RUNNING 条件更新成功时为 true
     * 本方法只访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public boolean finish(long taskId, PlanningTaskStatus status, Long planId, String code,
                                    String reason, Instant updatedAt) {
        Objects.requireNonNull(status, "status");
        if (!status.isTerminal()) throw new IllegalArgumentException("status");
        return write(() -> mapper.finish(taskId, status.name(), planId, code, reason, updatedAt)) == 1;
    }

    /**
     * @param taskId 正任务编号
     * @return 删除仍为 PENDING 的行时为 true
     * 本方法访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public boolean deletePending(long taskId) { return write(() -> mapper.deletePending(taskId)) == 1; }
    /**
     * @param updatedAt 非空恢复时间
     * @param code 非空 SERVER_RESTARTED 稳定码
     * @param reason 非空固定说明
     * @return 实际更新行数
     * 本方法只访问数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override public int failRunningTasks(Instant updatedAt, String code, String reason) {
        return write(() -> mapper.failRunning(updatedAt, code, reason));
    }

    private PlanningTaskRow row(PendingTask draft) {
        PlanningTaskRow row = new PlanningTaskRow();
        row.setUserId(draft.userId()); row.setRequestJson(codec.encodeRequest(draft.originalRequest()));
        row.setResolvedRequirementJson(codec.encodeResolved(draft.resolvedRequirement()));
        row.setAppliedPreferencesJson(codec.mapperWriteSnapshot(draft.appliedPreferences()));
        row.setCurrentStep(PlanningTaskStatus.PENDING.name()); row.setStatus(PlanningTaskStatus.PENDING.name());
        row.setCreatedAt(draft.createdAt()); row.setUpdatedAt(draft.createdAt()); return row;
    }

    private PlanningTask map(PlanningTaskRow row) {
        PlanningTaskStatus status = PlanningTaskStatus.fromStoredValue(row.getStatus());
        ResolvedTravelRequirement resolved = codec.decodeResolved(row.getResolvedRequirementJson());
        var snapshot = codec.decodeSnapshot(row.getAppliedPreferencesJson());
        if (!snapshot.equals(resolved.appliedPreferenceSnapshot())) throw PlanningTaskException.storageFailure();
        return new PlanningTask(row.getId(), row.getUserId(), codec.decodeRequest(row.getRequestJson()), resolved,
                snapshot, row.getCurrentStep(), status, row.getPlanId(),
                row.getFailureCode(), row.getFailureReason(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private Optional<PlanningTask> mapped(RowQuery query) {
        try { return Optional.ofNullable(query.get()).map(this::map); }
        catch (PlanningTaskException exception) { throw exception; }
        catch (RuntimeException exception) { throw PlanningTaskException.storageFailure(); }
    }

    private int write(IntWrite operation) {
        try { return operation.run(); }
        catch (RuntimeException exception) { throw PlanningTaskException.storageFailure(); }
    }

    @FunctionalInterface private interface RowQuery { PlanningTaskRow get(); }
    @FunctionalInterface private interface IntWrite { int run(); }
}
