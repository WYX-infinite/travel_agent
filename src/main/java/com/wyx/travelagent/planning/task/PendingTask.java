package com.wyx.travelagent.planning.task;

import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：把创建 planning_task 所需的冻结输入绑定为一次原子仓储命令。
 * 输入是可信用户、原始请求、有效需求、偏好快照和时间，输出可转换为 PENDING 任务；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它位于任务十四服务与仓储之间；不负责活动任务并发锁、SQL、执行器提交或前端 DTO。
 * @param userId 正用户编号
 * @param originalRequest 非空原始结构化请求
 * @param resolvedRequirement 非空已校验需求
 * @param appliedPreferences 非空冻结偏好快照
 * @param createdAt 非空创建时刻
 */
public record PendingTask(int userId, TravelRequirementInput originalRequest,
        ResolvedTravelRequirement resolvedRequirement, AppliedPreferenceSnapshot appliedPreferences,
        Instant createdAt) {
    /** 创建待落库任务；只校验内存值，不访问外部资源，也不处理认证。 */
    public PendingTask {
        if (userId <= 0) throw new IllegalArgumentException("userId");
        Objects.requireNonNull(originalRequest, "originalRequest");
        Objects.requireNonNull(resolvedRequirement, "resolvedRequirement");
        Objects.requireNonNull(appliedPreferences, "appliedPreferences");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!appliedPreferences.equals(resolvedRequirement.appliedPreferenceSnapshot())) {
            throw new IllegalArgumentException("INCONSISTENT_PREFERENCE_SNAPSHOT");
        }
    }

    /**
     * 使用数据库生成主键创建初始任务。
     * @param taskId 正数据库任务编号
     * @return currentStep=PENDING、status=PENDING 的不可变任务
     * 本方法只转换内存值，不访问外部资源，也不处理认证。
     */
    public PlanningTask toTask(long taskId) {
        return new PlanningTask(taskId, userId, originalRequest, resolvedRequirement,
                appliedPreferences, PlanningTaskStatus.PENDING.name(), PlanningTaskStatus.PENDING,
                null, null, null, createdAt, createdAt);
    }
}
