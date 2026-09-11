package com.wyx.travelagent.planning.task;

import java.time.Instant;

/** MyBatis 专用 planning_task 行对象；只承载已定义列，不含秘密、Prompt 或模型原文。 */
final class PlanningTaskRow {
    private Long id; private Integer userId; private String requestJson; private String resolvedRequirementJson;
    private String appliedPreferencesJson; private String currentStep; private String status; private String failureCode;
    private String failureReason; private Long planId; private Instant createdAt; private Instant updatedAt;

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Integer getUserId() { return userId; } public void setUserId(Integer value) { userId = value; }
    public String getRequestJson() { return requestJson; } public void setRequestJson(String value) { requestJson = value; }
    public String getResolvedRequirementJson() { return resolvedRequirementJson; } public void setResolvedRequirementJson(String value) { resolvedRequirementJson = value; }
    public String getAppliedPreferencesJson() { return appliedPreferencesJson; } public void setAppliedPreferencesJson(String value) { appliedPreferencesJson = value; }
    public String getCurrentStep() { return currentStep; } public void setCurrentStep(String value) { currentStep = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getFailureCode() { return failureCode; } public void setFailureCode(String value) { failureCode = value; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String value) { failureReason = value; }
    public Long getPlanId() { return planId; } public void setPlanId(Long value) { planId = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
