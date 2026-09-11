package com.wyx.travelagent.trace;

import java.time.LocalDateTime;

/**
 * 业务问题：承载 planning_trace 表的脱敏列值和 MyBatis 回填主键。
 * 本行不提供 Prompt、模型原文、API Key、历史对话或偏好历史字段。
 */
final class PlanningTraceRow {
    private Long id;
    private Long taskId;
    private Long planVersionId;
    private String step;
    private String status;
    private Long durationMs;
    private String inputSummary;
    private String outputSummary;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;

    /** 创建空行供 MyBatis 使用；不访问外部资源。 */
    public PlanningTraceRow() {
    }

    /** 创建完整待插入行；参数已由 PlanningTraceEvent 校验。 */
    public PlanningTraceRow(PlanningTraceEvent event, String detailsJson) {
        this.taskId = event.taskId();
        this.planVersionId = event.planVersionId();
        this.step = event.step().name();
        this.status = event.status().name();
        this.durationMs = event.durationMs();
        this.inputSummary = detailsJson;
        this.outputSummary = null;
        this.errorCode = event.errorCode();
        this.errorMessage = event.errorMessage();
        this.createdAt = LocalDateTime.ofInstant(event.createdAt(), java.time.ZoneOffset.UTC);
    }

    /** @return 数据库主键。 */ public Long getId() { return id; }
    /** @param id MyBatis 回填主键。 */ public void setId(Long id) { this.id = id; }
    /** @return 任务编号。 */ public Long getTaskId() { return taskId; }
    /** @return 可缺失版本编号。 */ public Long getPlanVersionId() { return planVersionId; }
    /** @return 稳定步骤。 */ public String getStep() { return step; }
    /** @return 稳定状态。 */ public String getStatus() { return status; }
    /** @return 耗时毫秒。 */ public Long getDurationMs() { return durationMs; }
    /** @return 字段白名单详情 JSON。 */ public String getInputSummary() { return inputSummary; }
    /** @return 当前未使用的输出摘要。 */ public String getOutputSummary() { return outputSummary; }
    /** @return 可缺失稳定错误码。 */ public String getErrorCode() { return errorCode; }
    /** @return 可缺失固定安全说明。 */ public String getErrorMessage() { return errorMessage; }
    /** @return UTC 创建时间。 */ public LocalDateTime getCreatedAt() { return createdAt; }
}
