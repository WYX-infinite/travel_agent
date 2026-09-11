package com.wyx.travelagent.planning.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 业务问题：把持久化 Trace 投影为安全 SSE 数据，不暴露详情 JSON、异常原文或外部响应。
 * 输入是已脱敏事件，输出为 JSON；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只服务任务十四进度流；不负责重放游标、所有权、连接生命周期或任务取消。
 * @param eventId 正 planning_trace 主键
 * @param taskId 正任务编号
 * @param step 稳定步骤
 * @param status 稳定事件状态
 * @param progressPercent 0 至 100 展示进度
 * @param message 固定安全说明
 * @param errorCode 可为空稳定失败码
 * @param occurredAt 非空事件时刻
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanningSseEvent(long eventId, long taskId, String step, String status, int progressPercent,
                               String message, String errorCode, Instant occurredAt) { }
