package com.wyx.travelagent.planning.task;

import com.wyx.travelagent.planning.PlanningOutcome;
import com.wyx.travelagent.planning.TravelPlanningCommand;
import com.wyx.travelagent.planning.TravelPlanningOrchestrator;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.PreferenceMergeResult;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务问题：为任务十四承载可持久化异步规划任务的应用服务边界。
 * 输入来自已认证 Controller，输出为冻结并持久化的任务状态；本服务访问偏好与任务数据库，并把工作提交到有界线程池后调用任务十三编排器，不访问文件。
 * 认证由上层入口负责；本类型位于偏好记忆、任务十三同步编排和任务十五前端契约之间，不负责 JWT 解析、SSE 连接、前端页面或直接操作 MyBatis Mapper。
 */
@Service
public final class PlanningTaskService {
    private static final String EXECUTION_FAILED = "PLANNING_TASK_EXECUTION_FAILED";
    private final PlanningTaskRepository repository;
    private final PreferenceMemoryService preferenceMemoryService;
    private final RequirementValidator requirementValidator;
    private final TravelPlanningOrchestrator orchestrator;
    private final Executor executor;
    private final PlanningTraceService traceService;
    private final Clock clock;

    /**
     * 创建异步任务应用服务。
     * @param repository 非空任务仓储
     * @param preferenceMemoryService 非空偏好合并服务
     * @param requirementValidator 非空完整需求校验器
     * @param orchestrator 非空任务十三同步编排器
     * @param executor 非空、有界且拒绝策略为 AbortPolicy 的规划执行器
     * @param traceService 非空脱敏 Trace 服务
     * @param clock 非空可测试时钟
     * 构造不访问外部资源也不处理认证；后续方法会访问数据库、偏好、线程池、模型和受控工具。
     */
    public PlanningTaskService(PlanningTaskRepository repository,
                               PreferenceMemoryService preferenceMemoryService,
                               RequirementValidator requirementValidator,
                               TravelPlanningOrchestrator orchestrator,
                               @Qualifier("planningTaskExecutor") Executor executor,
                               PlanningTraceService traceService,
                               Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.preferenceMemoryService = Objects.requireNonNull(preferenceMemoryService, "preferenceMemoryService");
        this.requirementValidator = Objects.requireNonNull(requirementValidator, "requirementValidator");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 冻结本次偏好与有效需求，原子创建 PENDING 任务并提交后台执行。
     * @param userId 认证层提供的正用户编号，不能由请求体覆盖
     * @param input 非空原始三态结构化请求；useSavedPreferences 为空时按 true 处理
     * @return 已持久化的 PENDING 任务快照
     * @throws PlanningTaskException 用户已有活动任务或有界队列已满时返回稳定错误码
     * 本方法访问偏好与任务数据库并提交线程池，不访问文件或外部网络；认证由上层入口负责，不直接调用模型。
     */
    public PlanningTask create(int userId, TravelRequirementInput input) {
        if (userId <= 0) throw new IllegalArgumentException("userId");
        Objects.requireNonNull(input, "input");
        PreferenceMergeResult merged = preferenceMemoryService.merge(userId, input);
        TravelRequirement requirement = requirementValidator.validate(merged.mergedInput());
        ResolvedTravelRequirement resolved = new ResolvedTravelRequirement(requirement, merged.snapshot());
        PendingTask draft = new PendingTask(userId, input, resolved, merged.snapshot(), Instant.now(clock));
        PlanningTask task = repository.createPendingIfNoActive(draft).orElseThrow(() ->
                new PlanningTaskException("PLANNING_TASK_ALREADY_ACTIVE",
                        "an active planning task already exists", HttpStatus.CONFLICT));
        try {
            executor.execute(() -> execute(task));
        } catch (RejectedExecutionException exception) {
            // 只有仍处于 PENDING 的任务才可删除，因此不会误删已被执行器领取的工作。
            if (!repository.deletePending(task.id())) throw PlanningTaskException.storageFailure();
            throw new PlanningTaskException("PLANNING_QUEUE_FULL",
                    "planning queue is full", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return task;
    }

    /**
     * 查询当前用户拥有的任务。
     * @param taskId 正任务编号
     * @param userId 认证层提供的正用户编号
     * @return 非空任务快照
     * @throws PlanningTaskException 不存在或不属于当前用户时统一返回 PLANNING_TASK_NOT_FOUND
     * 本方法只查询任务数据库，不访问模型、缓存、文件、线程池或网络；认证由上层入口负责，资源隔离使用显式 userId。
     */
    public PlanningTask getOwned(long taskId, int userId) {
        return repository.findOwned(taskId, userId).orElseThrow(() ->
                new PlanningTaskException("PLANNING_TASK_NOT_FOUND",
                        "planning task not found", HttpStatus.NOT_FOUND));
    }

    /**
     * 基于当前用户最近一次该行程任务的原始结构化请求创建重新规划任务。
     * @param planId 正行程编号，调用方必须先完成行程所有权检查
     * @param userId 认证层提供的正用户编号
     * @return 新建 PENDING 任务；本次会重新冻结当前长期偏好
     * @throws PlanningTaskException 找不到用户隔离的来源任务时返回 PLAN_NOT_FOUND
     * 本方法查询/写任务与偏好数据库并提交有界线程池，不访问文件或外部网络；认证由上层入口负责，不直接处理 SSE。
     */
    public PlanningTask replan(long planId, int userId) {
        PlanningTask source = repository.findLatestByPlanAndUser(planId, userId).orElseThrow(() ->
                new PlanningTaskException("PLAN_NOT_FOUND", "plan not found", HttpStatus.NOT_FOUND));
        return create(userId, source.originalRequest());
    }

    /**
     * 查询当前用户最近关联指定行程的任务，用于详情展示冻结偏好。
     * @param planId 正行程编号
     * @param userId 认证层提供的正用户编号
     * @return 可为空的最近任务
     * 本方法只查询任务数据库，不访问模型、缓存、文件、线程池或网络；认证由上层入口负责，显式 userId 保证资源隔离。
     */
    public Optional<PlanningTask> findLatestForPlan(long planId, int userId) {
        return repository.findLatestByPlanAndUser(planId, userId);
    }

    private void execute(PlanningTask task) {
        if (!repository.markRunning(task.id(), Instant.now(clock))) return;
        try {
            TravelPlanningCommand command = command(task);
            AtomicInteger highestStep = new AtomicInteger(0);
            PlanningOutcome outcome = orchestrator.plan(command, step -> {
                int next = stepRank(step);
                int previous = highestStep.getAndAccumulate(next, Math::max);
                if (next > previous) repository.updateStep(task.id(), step.name(), Instant.now(clock));
            });
            finish(task.id(), outcome);
        } catch (RuntimeException exception) {
            traceFailureSafely(task.id());
            repository.finish(task.id(), PlanningTaskStatus.FAILED, null,
                    EXECUTION_FAILED, "planning task execution failed", Instant.now(clock));
        }
    }

    private TravelPlanningCommand command(PlanningTask task) {
        String city = task.resolvedRequirement().requirement().destinationCity();
        ToolAction poi = new ToolAction("SEARCH_POI",
                Map.of("city", city, "keyword", "景点", "limit", 10));
        return new TravelPlanningCommand(task.id(), task.userId(), task.originalRequest(),
                city + " 旅行", new ToolExecutionPolicy(5), new ToolPlan(city, java.util.List.of(poi)),
                task.resolvedRequirement());
    }

    private void finish(long taskId, PlanningOutcome outcome) {
        PlanningTaskStatus status = PlanningTaskStatus.fromOutcome(outcome);
        if (status == PlanningTaskStatus.FAILED) {
            String code = outcome.failureCode() == null ? EXECUTION_FAILED : outcome.failureCode();
            repository.finish(taskId, status, outcome.planId(), code,
                    safeFailureReason(code), Instant.now(clock));
            return;
        }
        repository.finish(taskId, status, outcome.planId(), null, null, Instant.now(clock));
    }

    private static String safeFailureReason(String code) {
        return switch (code) {
            case "CONTEXT_BUDGET_EXCEEDED" -> "planning context budget exceeded";
            default -> "planning task execution failed";
        };
    }

    private static int stepRank(PlanningStep step) {
        return switch (step) {
            case REQUIREMENT -> 1;
            case KNOWLEDGE_RETRIEVAL -> 2;
            case TOOL_EXECUTION, PREPARATION -> 3;
            case GENERATION -> 4;
            case VALIDATION -> 5;
            case REPAIR -> 6;
            case COMPLETION -> 7;
        };
    }

    private void traceFailureSafely(long taskId) {
        try {
            traceService.failed(taskId, null, PlanningStep.COMPLETION, 1L, EXECUTION_FAILED);
        } catch (RuntimeException ignored) {
            // Trace 是审计副作用；失败补偿必须仍把主任务可靠地写入稳定终态。
        }
    }
}
