package com.wyx.travelagent.trace;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：表示一条可持久化、脱敏且与任务/版本关联的编排 Trace 事件。
 * 输入为稳定步骤、状态、耗时和字段白名单摘要，输出为不可变事件；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三只保存安全客观元数据；本类型不负责持久化、拼接异常文本或保存 Prompt/模型回复。
 *
 * @param taskId 正任务编号
 * @param planVersionId 版本事件对应的版本主键，否则为空
 * @param step 非空稳定步骤
 * @param status 非空结果状态
 * @param durationMs 非负耗时毫秒
 * @param details 非空字段白名单摘要
 * @param errorCode 失败时的稳定代码，否则为空
 * @param errorMessage 固定安全说明，否则为空；不得保存底层异常文本
 * @param createdAt 非空创建时刻
 */
public record PlanningTraceEvent(
        long taskId,
        Long planVersionId,
        PlanningStep step,
        TraceStatus status,
        long durationMs,
        PlanningTraceDetails details,
        String errorCode,
        String errorMessage,
        Instant createdAt) {

    /** 创建语义一致的脱敏事件；只校验内存字段，不访问外部资源，也不处理认证。 */
    public PlanningTraceEvent {
        if (taskId <= 0 || planVersionId != null && planVersionId <= 0 || durationMs < 0) {
            throw new IllegalArgumentException("INVALID_TRACE_EVENT");
        }
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == TraceStatus.FAILED && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("FAILED_TRACE_REQUIRES_CODE");
        }
        if (status == TraceStatus.FAILED && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("FAILED_TRACE_REQUIRES_MESSAGE");
        }
        if (status != TraceStatus.FAILED && (errorCode != null || errorMessage != null)) {
            throw new IllegalArgumentException("SUCCESS_TRACE_CANNOT_HAVE_CODE");
        }
    }
}
