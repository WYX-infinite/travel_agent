package com.wyx.travelagent.planning.task;

import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：冻结一条 planning_task 的原始请求、有效需求、偏好快照和状态事实。
 * 输入来自可信服务或数据库，输出为不可变任务；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它连接偏好记忆、任务十三同步编排与任务十五查询契约；不负责 JSON 编解码、异步执行、SSE 或直接暴露内部载荷。
 * @param id 正任务编号
 * @param userId 正用户编号
 * @param originalRequest 非空原始三态结构化请求
 * @param resolvedRequirement 非空已校验需求及冻结快照
 * @param appliedPreferences 非空且与 resolvedRequirement 一致的冻结偏好快照
 * @param currentStep 非空白稳定步骤
 * @param status 非空任务状态
 * @param planId 已创建行程时的正编号，否则为空
 * @param failureCode FAILED 时的稳定代码，否则为空
 * @param failureReason FAILED 时的固定脱敏说明，否则为空
 * @param createdAt 非空创建时刻
 * @param updatedAt 非空且不早于创建时刻的更新时间
 */
public record PlanningTask(long id, int userId, TravelRequirementInput originalRequest,
        ResolvedTravelRequirement resolvedRequirement, AppliedPreferenceSnapshot appliedPreferences,
        String currentStep, PlanningTaskStatus status, Long planId, String failureCode,
        String failureReason, Instant createdAt, Instant updatedAt) {

    /** 创建语义一致的不可变任务；只校验内存值，不访问外部资源，也不处理认证。 */
    public PlanningTask {
        if (id <= 0 || userId <= 0) throw new IllegalArgumentException("INVALID_PLANNING_TASK_IDENTITY");
        Objects.requireNonNull(originalRequest, "originalRequest");
        Objects.requireNonNull(resolvedRequirement, "resolvedRequirement");
        Objects.requireNonNull(appliedPreferences, "appliedPreferences");
        if (!appliedPreferences.equals(resolvedRequirement.appliedPreferenceSnapshot())) {
            throw new IllegalArgumentException("INCONSISTENT_PREFERENCE_SNAPSHOT");
        }
        if (currentStep == null || currentStep.isBlank()) throw new IllegalArgumentException("currentStep");
        Objects.requireNonNull(status, "status");
        if (planId != null && planId <= 0) throw new IllegalArgumentException("planId");
        if (status == PlanningTaskStatus.COMPLETED && planId == null) {
            throw new IllegalArgumentException("COMPLETED_REQUIRES_PLAN_ID");
        }
        if (status == PlanningTaskStatus.FAILED) {
            if (failureCode == null || failureCode.isBlank() || failureReason == null || failureReason.isBlank()) {
                throw new IllegalArgumentException("FAILED_REQUIRES_SAFE_ERROR");
            }
        } else if (failureCode != null || failureReason != null) {
            throw new IllegalArgumentException("NON_FAILED_CANNOT_HAVE_ERROR");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt");
    }

    /**
     * 复制任务并替换状态字段，供仓储 Fake 和类型安全映射使用。
     * @param newStatus 非空目标状态，必须满足固定状态机
     * @param newStep 非空白稳定步骤
     * @param newPlanId 可为空；COMPLETED 时必须为正数
     * @param newFailureCode FAILED 时非空白，否则为空
     * @param newFailureReason FAILED 时非空白固定说明，否则为空
     * @param newUpdatedAt 非空且不早于创建时间
     * @return 保留原始请求与冻结快照的新任务
     * @throws IllegalArgumentException 状态转换或字段组合非法时抛出
     * 本方法只复制内存对象，不访问外部资源，也不处理认证。
     */
    public PlanningTask withState(PlanningTaskStatus newStatus, String newStep, Long newPlanId,
                                  String newFailureCode, String newFailureReason, Instant newUpdatedAt) {
        if (!status.canTransitionTo(newStatus)) throw new IllegalArgumentException("ILLEGAL_TASK_TRANSITION");
        return new PlanningTask(id, userId, originalRequest, resolvedRequirement, appliedPreferences,
                newStep, newStatus, newPlanId, newFailureCode, newFailureReason, createdAt, newUpdatedAt);
    }
}
