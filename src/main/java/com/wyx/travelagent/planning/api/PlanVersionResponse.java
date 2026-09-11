package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 业务问题：以显式字段公开一个行程版本，避免直接序列化领域行程、校验报告或指标对象。
 * 输入是用户隔离且严格解析的版本，输出稳定 JSON DTO；映射不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四版本接口；不暴露 Row、内部 JSON、Prompt、模型回复或 Trace，也不负责任务十五页面。
 * @param versionId 正版本主键
 * @param versionNumber 正版本号
 * @param destinationCity 非空城市
 * @param startDate 非空首日
 * @param endDate 非空末日
 * @param estimatedTotalCost 非空预计总费用
 * @param days 非空显式日程 DTO
 * @param warnings 非空安全问题 DTO
 * @param metrics 非空显式指标 DTO
 * @param createdAt 非空创建时间
 */
public record PlanVersionResponse(long versionId, int versionNumber, String destinationCity,
        LocalDate startDate, LocalDate endDate, BigDecimal estimatedTotalCost,
        List<PlanDayResponse> days, List<PlanWarningResponse> warnings,
        PlanMetricsResponse metrics, Instant createdAt) {
    /**
     * @param version 非空且已用户隔离的类型化版本
     * @return 不含内部实体或 JSON 的稳定 DTO
     * 本方法只映射内存字段，不访问外部资源或处理认证；缺失数据由领域版本前置条件排除。
     */
    public static PlanVersionResponse from(TravelPlanRepository.PlanVersion version) {
        var plan = version.plan();
        return new PlanVersionResponse(version.id(), version.versionNumber(), plan.destinationCity(),
                plan.startDate(), plan.endDate(), plan.estimatedTotalCost(),
                plan.days().stream().map(PlanDayResponse::from).toList(),
                version.validationReport().issues().stream().map(PlanWarningResponse::from).toList(),
                PlanMetricsResponse.from(version.metrics()), version.createdAt());
    }
}
