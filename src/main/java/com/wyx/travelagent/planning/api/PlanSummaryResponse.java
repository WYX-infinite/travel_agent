package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 业务问题：公开行程列表所需主表事实，不泄露 userId 或内部 JSON。
 * 输入是用户隔离主记录，输出为 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它承接任务十三的持久化结果并服务行程列表；不负责版本内容、SSE 或重新规划。
 * @param planId 正行程编号
 * @param destinationCity 非空城市
 * @param startDate 非空首日
 * @param endDate 非空末日
 * @param currentVersion 非负当前版本
 * @param status 非空稳定状态
 * @param createdAt 非空创建时间
 * @param updatedAt 非空更新时间
 */
public record PlanSummaryResponse(long planId, String destinationCity, LocalDate startDate,
        LocalDate endDate, int currentVersion, String status, Instant createdAt, Instant updatedAt) {
    /**
     * @param plan 非空且已用户隔离的主记录
     * @return 不含 userId 的摘要
     * 本方法不访问外部资源或处理认证。
     */
    public static PlanSummaryResponse from(TravelPlanRepository.PlanRecord plan) {
        return new PlanSummaryResponse(plan.id(), plan.destinationCity(), plan.startDate(), plan.endDate(),
                plan.currentVersion(), plan.status().name(), plan.createdAt(), plan.updatedAt());
    }
}
