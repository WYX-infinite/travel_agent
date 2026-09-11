package com.wyx.travelagent.planning.api;

import java.time.Instant;

/**
 * 业务问题：在缺少完成 Trace（例如服务重启恢复）时，用 planning_task 的持久化事实发送独立终态快照。
 * 输入是已完成用户所有权检查的任务字段，输出名为 planning-status 的 SSE 数据；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它补充任务十三 Trace 进度但不伪造 planning_trace 主键；不负责历史重放、任务取消、任务十五页面或客户端重试。
 * @param taskId 正任务编号
 * @param status COMPLETED、NEEDS_ADJUSTMENT 或 FAILED
 * @param currentStep 可为空稳定步骤
 * @param progressPercent 固定为终态 100
 * @param planId 可为空行程编号
 * @param errorCode 可为空稳定失败码
 * @param errorMessage 可为空固定脱敏失败说明
 * @param occurredAt 非空任务更新时间
 */
public record PlanningTaskSseEvent(long taskId, String status, String currentStep,
        int progressPercent, Long planId, String errorCode, String errorMessage, Instant occurredAt) { }
