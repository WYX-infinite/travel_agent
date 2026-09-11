package com.wyx.travelagent.trace;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：统一创建并落库脱敏 Trace，避免编排器拼接用户文本或底层异常。
 * 输入仅允许稳定枚举、错误码和字段白名单详情；输出写入仓储，不保存完整 Prompt、回复、Key、对话或偏好历史。
 * 本类型通过仓储访问数据库，不访问模型、缓存、文件或网络，认证由上层入口负责。任务十三只记录同步编排；本类型不负责查询、异步推送或保存原始内容。
 */
public final class PlanningTraceService {
    private final PlanningTraceRepository repository;
    private final Clock clock;

    /**
     * 创建统一 Trace 服务。
     *
     * @param repository 非空 Trace 仓储
     * @param clock 非空可测试时钟
     * 构造不访问外部资源，也不处理认证。
     */
    public PlanningTraceService(PlanningTraceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 持久化成功或降级事件。
     *
     * @param taskId 正任务编号
     * @param planVersionId 可缺失版本编号
     * @param step 非空步骤
     * @param status 非空且只能是 SUCCEEDED 或 DEGRADED
     * @param durationMs 非负耗时
     * @param details 非空脱敏详情
     * 本方法仅通过仓储写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public void record(
            long taskId,
            Long planVersionId,
            PlanningStep step,
            TraceStatus status,
            long durationMs,
            PlanningTraceDetails details) {
        repository.append(new PlanningTraceEvent(taskId, planVersionId, step, status,
                durationMs, details, null, null, Instant.now(clock)));
    }

    /**
     * 只用稳定代码和固定安全说明持久化失败事件。
     *
     * @param taskId 正任务编号
     * @param planVersionId 可缺失版本编号
     * @param step 非空失败步骤
     * @param durationMs 非负耗时
     * @param code 非空且仅允许大写字母、数字和下划线的稳定代码
     * 本方法仅通过仓储写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public void failed(
            long taskId,
            Long planVersionId,
            PlanningStep step,
            long durationMs,
            String code) {
        if (code == null || !code.matches("[A-Z0-9_]{2,64}")) {
            throw new IllegalArgumentException("INVALID_TRACE_ERROR_CODE");
        }
        repository.append(new PlanningTraceEvent(taskId, planVersionId, step, TraceStatus.FAILED,
                durationMs, PlanningTraceDetails.empty(), code, safeMessage(step), Instant.now(clock)));
    }

    private static String safeMessage(PlanningStep step) {
        return switch (Objects.requireNonNull(step, "step")) {
            case REQUIREMENT -> "requirement preparation failed";
            case PREPARATION -> "planning facts are unavailable";
            case GENERATION -> "itinerary generation failed";
            case VALIDATION -> "itinerary validation failed";
            case REPAIR -> "itinerary repair failed";
            case COMPLETION -> "planning completion failed";
        };
    }
}
