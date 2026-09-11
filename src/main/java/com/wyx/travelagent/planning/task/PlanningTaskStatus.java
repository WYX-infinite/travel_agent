package com.wyx.travelagent.planning.task;

import com.wyx.travelagent.planning.PlanningOutcome;
import com.wyx.travelagent.planning.PlanningOutcomeStatus;

import java.util.Objects;

/**
 * 业务问题：用稳定状态表达可持久化异步规划任务，并集中约束合法转换。
 * 输入是数据库状态或任务十三同步结果，输出为任务状态；本枚举不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它连接任务十三的同步结果与任务十五前端契约；不负责任务执行、HTTP 映射或行程持久化。
 */
public enum PlanningTaskStatus {
    /** 已落库但尚未被后台线程实际领取。 */
    PENDING,
    /** 后台线程已经通过条件更新取得唯一执行权。 */
    RUNNING,
    /** 已生成无硬错误的行程，并且必须关联正 planId。 */
    COMPLETED,
    /** 有可查询版本，但有限修正后仍需用户调整约束。 */
    NEEDS_ADJUSTMENT,
    /** 任务以稳定错误码终止。 */
    FAILED;

    /**
     * 判断当前状态能否转换到目标状态。
     * @param target 非空目标状态
     * @return 同状态幂等写或固定状态机允许的转换返回 true
     * 终态只能保持自身，不能重新进入 RUNNING；本方法只做内存判断，不访问外部资源，也不处理认证。
     */
    public boolean canTransitionTo(PlanningTaskStatus target) {
        Objects.requireNonNull(target, "target");
        if (this == target) return true;
        return switch (this) {
            case PENDING -> target == RUNNING;
            case RUNNING -> target == COMPLETED || target == NEEDS_ADJUSTMENT || target == FAILED;
            case COMPLETED, NEEDS_ADJUSTMENT, FAILED -> false;
        };
    }

    /**
     * 严格解析数据库值。
     * @param value 非空、非空白的数据库状态文本
     * @return 已知任务状态
     * @throws PlanningTaskException 状态未知时使用 PLANNING_TASK_STORAGE_FAILED
     * 未知值绝不能被当作完成；本方法不访问数据库、模型、缓存、文件、线程池或网络，认证由调用层负责。
     */
    public static PlanningTaskStatus fromStoredValue(String value) {
        try {
            return PlanningTaskStatus.valueOf(Objects.requireNonNull(value, "value"));
        } catch (RuntimeException exception) {
            throw PlanningTaskException.storageFailure();
        }
    }

    /**
     * 把任务十三同步结果确定性映射为异步任务状态。
     * @param outcome 非空同步规划结果
     * @return COMPLETED、NEEDS_ADJUSTMENT 或 FAILED
     * @throws IllegalArgumentException 当 COMPLETED 缺少 planId
     * 本方法只读取内存结果，不访问外部资源，也不处理认证。
     */
    public static PlanningTaskStatus fromOutcome(PlanningOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.status() == PlanningOutcomeStatus.COMPLETED && outcome.planId() == null) {
            throw new IllegalArgumentException("COMPLETED_REQUIRES_PLAN_ID");
        }
        return switch (outcome.status()) {
            case COMPLETED -> COMPLETED;
            case NEEDS_ADJUSTMENT -> NEEDS_ADJUSTMENT;
            case CONTEXT_BUDGET_EXCEEDED, FAILED -> FAILED;
        };
    }

    /**
     * 判断任务是否仍占用当前用户的活动名额。
     * @return PENDING 或 RUNNING 时为 true
     * 本方法无输入和空值语义，只做内存判断，不访问外部资源，也不处理认证。
     */
    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * 判断任务是否已经到达不可逆终态。
     * @return COMPLETED、NEEDS_ADJUSTMENT 或 FAILED 时为 true
     * 本方法无输入和空值语义，只做内存判断，不访问外部资源，也不处理认证。
     */
    public boolean isTerminal() {
        return !isActive();
    }
}
