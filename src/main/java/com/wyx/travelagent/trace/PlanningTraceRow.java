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
    /** @param value MyBatis 读取的任务编号。 */ public void setTaskId(Long value) { taskId = value; }
    /** @return 可缺失版本编号。 */ public Long getPlanVersionId() { return planVersionId; }
    /** @param value MyBatis 读取的版本编号，可为空。 */ public void setPlanVersionId(Long value) { planVersionId = value; }
    /** @return 稳定步骤。 */ public String getStep() { return step; }
    /** @param value MyBatis 读取的步骤。 */ public void setStep(String value) { step = value; }
    /** @return 稳定状态。 */ public String getStatus() { return status; }
    /** @param value MyBatis 读取的状态。 */ public void setStatus(String value) { status = value; }
    /** @return 耗时毫秒。 */ public Long getDurationMs() { return durationMs; }
    /** @param value MyBatis 读取的耗时。 */ public void setDurationMs(Long value) { durationMs = value; }
    /** @return 字段白名单详情 JSON。 */ public String getInputSummary() { return inputSummary; }
    /** @param value MyBatis 读取的详情 JSON。 */ public void setInputSummary(String value) { inputSummary = value; }
    /** @return 当前未使用的输出摘要。 */ public String getOutputSummary() { return outputSummary; }
    /** @param value MyBatis 读取的输出摘要，可为空。 */ public void setOutputSummary(String value) { outputSummary = value; }
    /** @return 可缺失稳定错误码。 */ public String getErrorCode() { return errorCode; }
    /** @param value MyBatis 读取的稳定错误码，可为空。 */ public void setErrorCode(String value) { errorCode = value; }
    /** @return 可缺失固定安全说明。 */ public String getErrorMessage() { return errorMessage; }
    /** @param value MyBatis 读取的固定错误说明，可为空。 */ public void setErrorMessage(String value) { errorMessage = value; }
    /** @return UTC 创建时间。 */ public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param value MyBatis 读取的 UTC 创建时间。 */ public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
