package com.wyx.travelagent.planning.persistence;

import java.time.Instant;

/**
 * 业务问题：承载 travel_plan_version 的结构化 JSON 快照和 MyBatis 回填主键。
 * JSON 由内部 ObjectMapper 从白名单领域对象生成，不接收模型原文。
 */
final class TravelPlanVersionRow {
    private Long id;
    private Long planId;
    private Integer versionNumber;
    private String itineraryJson;
    private String validationJson;
    private String metricsJson;
    private Instant createdAt;

    /** 创建空行供 MyBatis 使用；不访问外部资源。 */
    public TravelPlanVersionRow() {
    }

    /** 创建待插入版本行；调用方保证参数非空且版本为正。 */
    public TravelPlanVersionRow(Long planId, Integer versionNumber, String itineraryJson,
                                String validationJson, String metricsJson) {
        this.planId = planId;
        this.versionNumber = versionNumber;
        this.itineraryJson = itineraryJson;
        this.validationJson = validationJson;
        this.metricsJson = metricsJson;
    }

    /** @return 数据库版本主键。 */ public Long getId() { return id; }
    /** @param id MyBatis 回填的版本主键。 */ public void setId(Long id) { this.id = id; }
    /** @return 行程主键。 */ public Long getPlanId() { return planId; }
    /** @param value MyBatis 读取的行程主键。 */ public void setPlanId(Long value) { planId = value; }
    /** @return 行程内版本号。 */ public Integer getVersionNumber() { return versionNumber; }
    /** @param value MyBatis 读取的版本号。 */ public void setVersionNumber(Integer value) { versionNumber = value; }
    /** @return 类型化行程 JSON。 */ public String getItineraryJson() { return itineraryJson; }
    /** @param value MyBatis 读取的行程 JSON。 */ public void setItineraryJson(String value) { itineraryJson = value; }
    /** @return 全量校验报告 JSON。 */ public String getValidationJson() { return validationJson; }
    /** @param value MyBatis 读取的校验 JSON。 */ public void setValidationJson(String value) { validationJson = value; }
    /** @return 客观指标 JSON。 */ public String getMetricsJson() { return metricsJson; }
    /** @param value MyBatis 读取的指标 JSON。 */ public void setMetricsJson(String value) { metricsJson = value; }
    /** @return 版本创建时间。 */ public Instant getCreatedAt() { return createdAt; }
    /** @param value MyBatis 读取的创建时间。 */ public void setCreatedAt(Instant value) { createdAt = value; }
}
