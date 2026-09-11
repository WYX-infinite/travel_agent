package com.wyx.travelagent.planning.persistence;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 业务问题：承载 travel_plan 插入所需列和 MyBatis 回填主键。
 * 仅供持久化适配器使用，不包含 Prompt、模型回复或认证秘密。
 */
final class TravelPlanRow {
    private Long id;
    private Integer userId;
    private String destinationCity;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer currentVersion;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    /** 创建空行供 MyBatis 使用；不访问外部资源。 */
    public TravelPlanRow() {
    }

    /**
     * 创建待插入主记录。
     *
     * @param userId 正用户编号
     * @param destinationCity 非空城市
     * @param startDate 非空开始日期
     * @param endDate 非空结束日期
     * @param status 非空状态
     */
    public TravelPlanRow(Integer userId, String destinationCity, LocalDate startDate,
                         LocalDate endDate, String status) {
        this.userId = userId;
        this.destinationCity = destinationCity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentVersion = 0;
        this.status = status;
    }

    /** @return 数据库主键。 */ public Long getId() { return id; }
    /** @param id MyBatis 回填的数据库主键。 */ public void setId(Long id) { this.id = id; }
    /** @return 用户编号。 */ public Integer getUserId() { return userId; }
    /** @return 目的城市。 */ public String getDestinationCity() { return destinationCity; }
    /** @return 开始日期。 */ public LocalDate getStartDate() { return startDate; }
    /** @return 结束日期。 */ public LocalDate getEndDate() { return endDate; }
    /** @return 当前版本号。 */ public Integer getCurrentVersion() { return currentVersion; }
    /** @return 行程状态。 */ public String getStatus() { return status; }
    /** @return 创建时间。 */ public Instant getCreatedAt() { return createdAt; }
    /** @param value MyBatis 读取的创建时间。 */ public void setCreatedAt(Instant value) { createdAt = value; }
    /** @return 更新时间。 */ public Instant getUpdatedAt() { return updatedAt; }
    /** @param value MyBatis 读取的更新时间。 */ public void setUpdatedAt(Instant value) { updatedAt = value; }
    /** @param value MyBatis 读取的用户编号。 */ public void setUserId(Integer value) { userId = value; }
    /** @param value MyBatis 读取的目的城市。 */ public void setDestinationCity(String value) { destinationCity = value; }
    /** @param value MyBatis 读取的开始日期。 */ public void setStartDate(LocalDate value) { startDate = value; }
    /** @param value MyBatis 读取的结束日期。 */ public void setEndDate(LocalDate value) { endDate = value; }
    /** @param value MyBatis 读取的当前版本。 */ public void setCurrentVersion(Integer value) { currentVersion = value; }
    /** @param value MyBatis 读取的状态。 */ public void setStatus(String value) { status = value; }
}
