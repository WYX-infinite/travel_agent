package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.task.PlanningTask;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 业务问题：把行程主记录、当前版本、冻结偏好和版本摘要组合为稳定详情契约。
 * 输入均已通过 userId 所有权过滤，输出不含数据库 Row、领域实体、内部 JSON 或 Trace；映射只读内存，不访问数据库、模型、缓存、文件、线程池或网络。
 * 它承接任务十三的持久化结果并服务任务十四 API；认证和查询由上层负责，天气事实缺失时明确返回 null，不负责任务十五页面或补造事实。
 * @param planId 正行程编号
 * @param status 非空稳定行程状态
 * @param currentVersion 非负当前版本号
 * @param destinationCity 非空目的城市
 * @param startDate 非空首日
 * @param endDate 非空末日
 * @param summary 非空、由主记录事实生成的摘要
 * @param weather 可为空；null 表示当前没有持久化天气事实
 * @param appliedPreferences 非空实际采用的长期偏好投影
 * @param days 非空日程列表；无当前版本时为空
 * @param warnings 非空安全问题列表；无当前版本时为空
 * @param metrics 可为空；无当前版本时为 null
 * @param versions 非空版本摘要列表
 * @param estimatedTotalCost 可为空；无当前版本时为 null
 * @param createdAt 非空创建时间
 * @param updatedAt 非空更新时间
 */
public record PlanDetailResponse(long planId, String status, int currentVersion,
        String destinationCity, LocalDate startDate, LocalDate endDate, String summary, Object weather,
        AppliedPreferencesResponse appliedPreferences, List<PlanDayResponse> days,
        List<PlanWarningResponse> warnings, PlanMetricsResponse metrics,
        List<PlanVersionSummaryResponse> versions, BigDecimal estimatedTotalCost,
        Instant createdAt, Instant updatedAt) {

    /**
     * 映射详情并把当前版本作为唯一版本摘要，供简单调用方和测试使用。
     * @param plan 非空且已用户隔离的主记录
     * @param current 可为空当前版本；为空时日程、问题和指标保持空/null
     * @param sourceTask 可为空最近任务；为空时返回未启用且无采用字段的偏好对象
     * @return 非空稳定详情 DTO
     * 本方法不访问外部资源或处理认证，不返回稳定错误码。
     */
    public static PlanDetailResponse from(TravelPlanRepository.PlanRecord plan,
                                          TravelPlanRepository.PlanVersion current,
                                          PlanningTask sourceTask) {
        return from(plan, current, sourceTask, current == null ? List.of() : List.of(current));
    }

    /**
     * 映射包含完整版本摘要的详情。
     * @param plan 非空且已用户隔离的主记录
     * @param current 可为空当前版本；为空时日程、问题和指标保持空/null
     * @param sourceTask 可为空最近任务；为空时返回稳定空偏好投影
     * @param allVersions 非空、已按版本号排序且已完成所有权过滤的版本
     * @return 非空稳定详情 DTO
     * 本方法只逐字段读取内存对象，不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
     */
    public static PlanDetailResponse from(TravelPlanRepository.PlanRecord plan,
                                          TravelPlanRepository.PlanVersion current,
                                          PlanningTask sourceTask,
                                          List<TravelPlanRepository.PlanVersion> allVersions) {
        var itinerary = current == null ? null : current.plan();
        return new PlanDetailResponse(plan.id(), plan.status().name(), plan.currentVersion(),
                plan.destinationCity(), plan.startDate(), plan.endDate(),
                plan.destinationCity() + " itinerary from " + plan.startDate() + " to " + plan.endDate(),
                null, AppliedPreferencesResponse.from(sourceTask == null ? null : sourceTask.appliedPreferences()),
                itinerary == null ? List.of() : itinerary.days().stream().map(PlanDayResponse::from).toList(),
                current == null ? List.of() : current.validationReport().issues().stream().map(PlanWarningResponse::from).toList(),
                current == null ? null : PlanMetricsResponse.from(current.metrics()),
                allVersions.stream().map(PlanVersionSummaryResponse::from).toList(),
                itinerary == null ? null : itinerary.estimatedTotalCost(), plan.createdAt(), plan.updatedAt());
    }
}
