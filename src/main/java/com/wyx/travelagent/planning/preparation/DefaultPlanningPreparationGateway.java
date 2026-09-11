package com.wyx.travelagent.planning.preparation;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalException;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalResult;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalService;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionBundle;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
import com.wyx.travelagent.tools.dispatch.TravelToolType;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceDetails;
import com.wyx.travelagent.trace.TraceStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：把任务十工具结果和任务十一知识引用转换为任务十二可消费的可信事实快照。
 * 输入是已校验需求、查询与受控工具计划，输出不含第三方原始 JSON；本类型不调用行程模型、不访问偏好仓储或处理认证。
 * POI 是生成前必需来源；天气与知识失败会显式降级为空事实，不会补造结论。
 * 本类型按需访问知识索引、工具、缓存和网络，认证由上层入口负责。任务十三只准备可信事实；本类型不负责生成、校验或持久化行程。
 */
public final class DefaultPlanningPreparationGateway implements PlanningPreparationGateway {
    private final KnowledgeRetrievalService knowledgeService;
    private final TravelToolDispatcher toolDispatcher;

    /**
     * @param knowledgeService 非空城市白名单知识检索服务
     * @param toolDispatcher 非空受控工具调度器
     * 构造不访问数据库、模型、缓存、文件或网络，也不处理认证。
     */
    public DefaultPlanningPreparationGateway(
            KnowledgeRetrievalService knowledgeService,
            TravelToolDispatcher toolDispatcher) {
        this.knowledgeService = Objects.requireNonNull(knowledgeService, "knowledgeService");
        this.toolDispatcher = Objects.requireNonNull(toolDispatcher, "toolDispatcher");
    }

    /**
     * 执行知识检索和白名单工具计划并生成内部事实。
     * POI 搜索失败或没有真实 poiId 时抛出 POI_DATA_UNAVAILABLE；天气失败只让 weather 为空。
     *
     * @param requirement 非空且已校验的不可变需求
     * @param knowledgeQuery 非空知识查询
     * @param policy 非空受控工具策略
     * @param plan 非空白名单工具计划
     * @return 非空可信事实与脱敏工具摘要
     * @throws PlanningPreparationException 当 POI 必需来源不可用或工具阶段失败
     * 本方法按需访问知识、工具、缓存和网络，不调用行程模型；认证由上层入口负责。
     */
    @Override
    public PlanningPreparationResult prepare(
            TravelRequirement requirement,
            String knowledgeQuery,
            ToolExecutionPolicy policy,
            ToolPlan plan) {
        return prepare(requirement, knowledgeQuery, policy, plan, PlanningPreparationObserver.none());
    }

    /**
     * 执行并观察真实知识检索与工具阶段。
     * @param requirement 非空已校验需求
     * @param knowledgeQuery 非空知识查询
     * @param policy 非空工具策略
     * @param plan 非空白名单计划
     * @param observer 非空观察器，只接收脱敏状态和摘要
     * @return 非空可信事实与工具摘要
     * 本方法访问知识索引、工具、缓存和网络，不调用行程模型；认证由上层负责，观察器不接收原始响应。
     */
    @Override
    public PlanningPreparationResult prepare(TravelRequirement requirement, String knowledgeQuery,
                                              ToolExecutionPolicy policy, ToolPlan plan,
                                              PlanningPreparationObserver observer) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(observer, "observer");
        observer.started(PlanningStep.KNOWLEDGE_RETRIEVAL);
        long knowledgeStarted = System.nanoTime();
        KnowledgeRetrievalResult knowledge;
        TraceStatus knowledgeStatus;
        try {
            knowledge = knowledgeService.retrieve(knowledgeQuery, requirement.destinationCity());
            knowledgeStatus = TraceStatus.SUCCEEDED;
        } catch (KnowledgeRetrievalException exception) {
            knowledge = new KnowledgeRetrievalResult(List.of(), List.of());
            knowledgeStatus = TraceStatus.DEGRADED;
        }
        observer.completed(PlanningStep.KNOWLEDGE_RETRIEVAL, knowledgeStatus,
                elapsed(knowledgeStarted), PlanningTraceDetails.empty());

        observer.started(PlanningStep.TOOL_EXECUTION);
        long toolStarted = System.nanoTime();
        ToolExecutionBundle bundle;
        try {
            bundle = toolDispatcher.execute(policy, plan);
        } catch (RuntimeException exception) {
            observer.failed(PlanningStep.TOOL_EXECUTION, elapsed(toolStarted), "TOOL_PREPARATION_FAILED");
            throw new PlanningPreparationException("TOOL_PREPARATION_FAILED");
        }

        List<ValidationPoiFact> places = new ArrayList<>();
        List<RouteFact> routes = new ArrayList<>();
        List<WeatherResult.DailyForecast> forecasts = new ArrayList<>();
        List<PlanningPreparationResult.WeatherCoordinateSummary> coordinates = new ArrayList<>();
        Set<String> seenPoiIds = new LinkedHashSet<>();
        boolean poiActionSeen = false;
        boolean poiFailure = false;

        List<ToolAction> actions = plan.actions() == null ? List.of() : plan.actions();
        for (int index = 0; index < bundle.executions().size(); index++) {
            ToolExecutionBundle.Execution execution = bundle.executions().get(index);
            ToolAction action = index < actions.size() ? actions.get(index) : null;
            if (execution.type() == TravelToolType.SEARCH_POI) {
                poiActionSeen = true;
                if (!execution.result().success()) {
                    poiFailure = true;
                } else if (execution.result().data() instanceof List<?> values) {
                    for (Object value : values) {
                        if (value instanceof PlaceCandidate place && place.poiId() != null
                                && seenPoiIds.add(place.poiId())) {
                            places.add(new ValidationPoiFact(place, places.size(), false, false,
                                    OpeningHoursFact.unknown()));
                        }
                    }
                }
            } else if (isRoute(execution.type()) && execution.result().success()
                    && execution.result().data() instanceof TravelSegment segment && action != null) {
                Object origin = action.arguments().get("origin");
                Object destination = action.arguments().get("destination");
                if (origin instanceof PlaceCandidate from && destination instanceof PlaceCandidate to
                        && from.poiId() != null && to.poiId() != null) {
                    routes.add(new RouteFact(from.poiId(), to.poiId(), segment));
                }
            } else if (execution.type() == TravelToolType.WEATHER_FORECAST && action != null) {
                if (execution.originalCoordinateSystem() != null
                        && execution.requestedCoordinateSystem() != null) {
                    coordinates.add(new PlanningPreparationResult.WeatherCoordinateSummary(
                            execution.originalCoordinateSystem(), execution.requestedCoordinateSystem()));
                }
                if (execution.result().success()
                        && execution.result().data() instanceof WeatherResult weather) {
                    forecasts.addAll(weather.dailyForecasts());
                }
            }
        }
        if (!poiActionSeen || poiFailure || places.isEmpty()) {
            observer.failed(PlanningStep.TOOL_EXECUTION, elapsed(toolStarted), "POI_DATA_UNAVAILABLE");
            throw new PlanningPreparationException("POI_DATA_UNAVAILABLE");
        }
        WeatherResult weather = forecasts.isEmpty() ? null : new WeatherResult(forecasts.stream()
                .distinct().sorted(java.util.Comparator.comparing(WeatherResult.DailyForecast::date)).toList());
        ItineraryValidationFacts facts = new ItineraryValidationFacts(
                places, routes, List.of(), weather, 0, knowledge.allowedReferences());
        int cacheHits = (int) bundle.executions().stream().filter(ToolExecutionBundle.Execution::cacheHit).count();
        PlanningPreparationResult.ToolSummary summary = new PlanningPreparationResult.ToolSummary(
                cacheHits, bundle.providerCallCounts(), coordinates, weather != null);
        observer.completed(PlanningStep.TOOL_EXECUTION,
                weather != null ? TraceStatus.SUCCEEDED : TraceStatus.DEGRADED,
                elapsed(toolStarted), PlanningTraceDetails.preparation(summary));
        return new PlanningPreparationResult(facts, summary);
    }

    private static long elapsed(long started) { return Math.max(1L, (System.nanoTime() - started) / 1_000_000L); }

    private static boolean isRoute(TravelToolType type) {
        return type == TravelToolType.WALKING_ROUTE
                || type == TravelToolType.DRIVING_ROUTE
                || type == TravelToolType.TRANSIT_ROUTE;
    }
}
