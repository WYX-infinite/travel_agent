package com.wyx.travelagent.planning.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wyx.travelagent.planning.task.PlanningTask;
import com.wyx.travelagent.planning.task.PlanningTaskStatus;

import java.time.Instant;

/**
 * 业务问题：以稳定字段公开异步规划任务状态和单调进度，不暴露冻结 JSON 或内部异常。
 * 输入是已完成所有权检查的任务，输出为 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四状态与创建响应；不负责 SSE Trace、执行任务或读取其他用户数据。
 * @param taskId 正任务编号
 * @param status 稳定状态名称
 * @param currentStep 稳定阶段名称
 * @param progressPercent 0 至 100 的单调展示进度
 * @param planId 可为空的行程编号
 * @param errorCode 可为空的稳定失败码
 * @param errorMessage 可为空的固定脱敏失败说明
 * @param createdAt 非空创建时刻
 * @param updatedAt 非空更新时间
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanningTaskResponse(long taskId, String status, String currentStep, int progressPercent,
        Long planId, String errorCode, String errorMessage, Instant createdAt, Instant updatedAt) {
    /**
     * 映射任务快照。
     * @param task 非空且已经用户隔离的任务
     * @return 非空响应；终态固定为 100
     * 本方法不访问数据库、模型、缓存、文件或网络；认证由上层入口负责，未知步骤保守显示 0。
     */
    public static PlanningTaskResponse from(PlanningTask task) {
        return new PlanningTaskResponse(task.id(), task.status().name(), task.currentStep(), progress(task),
                task.planId(), task.failureCode(), task.failureReason(), task.createdAt(), task.updatedAt());
    }

    private static int progress(PlanningTask task) {
        if (task.status().isTerminal()) return 100;
        if (task.status() == PlanningTaskStatus.PENDING) return 0;
        return switch (task.currentStep()) {
            case "REQUIREMENT" -> 10;
            case "KNOWLEDGE_RETRIEVAL" -> 25;
            case "TOOL_EXECUTION", "PREPARATION" -> 40;
            case "GENERATION" -> 60;
            case "VALIDATION", "REPAIR" -> 80;
            case "COMPLETION" -> 100;
            default -> 0;
        };
    }
}
