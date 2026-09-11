package com.wyx.travelagent.planning.persistence;

import java.time.LocalDate;

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
}
