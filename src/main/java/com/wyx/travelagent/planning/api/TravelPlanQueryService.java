package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.task.PlanningTask;
import com.wyx.travelagent.planning.task.PlanningTaskException;
import com.wyx.travelagent.planning.task.PlanningTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 业务问题：集中执行行程所有权检查、当前版本选择和重新规划来源衔接。
 * 输入始终带认证层 userId，输出显式 DTO 或新任务；本服务查询行程/任务数据库并可能提交有界线程池，不访问模型、文件或网络。
 * 它承接任务十三持久化结果；认证由上层入口负责，不负责 JWT、SSE、Mapper 行或任务十五页面。
 */
@Service
public final class TravelPlanQueryService {
    private final TravelPlanRepository plans; private final PlanningTaskService tasks;
    /**
     * @param plans 非空行程仓储
     * @param tasks 非空任务服务
     * 构造不访问数据库、模型、缓存、文件或网络；认证由上层入口负责。
     */
    public TravelPlanQueryService(TravelPlanRepository plans, PlanningTaskService tasks) {
        this.plans = Objects.requireNonNull(plans, "plans"); this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    /**
     * @param userId 正可信用户编号
     * @return 创建时间倒序摘要
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public List<PlanSummaryResponse> list(int userId) {
        return safeRead(() -> plans.findAllByUserId(userId).stream().map(PlanSummaryResponse::from).toList());
    }

    /**
     * @param planId 正行程编号
     * @param userId 正可信用户编号
     * @return 用户拥有的详情；跨用户与不存在统一 404
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public PlanDetailResponse detail(long planId, int userId) {
        return safeRead(() -> {
            TravelPlanRepository.PlanRecord plan = owned(planId, userId);
            List<TravelPlanRepository.PlanVersion> versions = plans.findVersionsByPlanIdAndUserId(planId, userId);
            TravelPlanRepository.PlanVersion current = versions.stream()
                    .filter(version -> version.versionNumber() == plan.currentVersion()).findFirst().orElse(null);
            if (plan.currentVersion() > 0 && current == null) throw planStorageFailure();
            PlanningTask source = tasks.findLatestForPlan(planId, userId).orElse(null);
            return PlanDetailResponse.from(plan, current, source, versions);
        });
    }

    /**
     * @param planId 正行程编号
     * @param userId 正可信用户编号
     * @return 版本号升序 DTO；跨用户与不存在统一 404
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public List<PlanVersionResponse> versions(long planId, int userId) {
        return safeRead(() -> {
            owned(planId, userId);
            return plans.findVersionsByPlanIdAndUserId(planId, userId).stream()
                    .map(PlanVersionResponse::from).toList();
        });
    }

    /**
     * @param planId 正行程编号
     * @param userId 正可信用户编号
     * @return 新 PENDING 任务
     * 本方法访问任务/偏好数据库与线程池，不访问文件或外部网络；认证由上层入口负责。
     */
    public PlanningTask replan(long planId, int userId) { owned(planId, userId); return tasks.replan(planId, userId); }

    private TravelPlanRepository.PlanRecord owned(long planId, int userId) {
        return safeRead(() -> plans.findByIdAndUserId(planId, userId).orElseThrow(() ->
                new PlanningTaskException("PLAN_NOT_FOUND", "plan not found", HttpStatus.NOT_FOUND)));
    }

    private static <T> T safeRead(Supplier<T> read) {
        try {
            return read.get();
        } catch (PlanningTaskException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 仓储异常可能包含 SQL、路径或损坏 JSON；API 边界只允许固定脱敏错误。
            throw planStorageFailure();
        }
    }

    private static PlanningTaskException planStorageFailure() {
        return new PlanningTaskException("PLAN_STORAGE_FAILED", "plan storage failed",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
