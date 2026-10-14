package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.context.PromptContextInput;
import com.wyx.travelagent.planning.context.ContextBudgetProperties;
import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.planning.context.ItineraryPromptRenderer;
import com.wyx.travelagent.planning.generation.ItineraryGenerationResult;
import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.generation.LangChain4jItineraryGenerator;
import dev.langchain4j.model.chat.ChatModel;
import com.wyx.travelagent.planning.model.CostFact;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.ValidationPoiFact;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务问题：把一次行程修正限制在预算内结构化事实，并执行恰好一次修正模型调用。
 * 输入是 {@link RepairRequest} 与原始可信事实快照，输出为可解析修正版及模型元数据；本服务不访问数据库、缓存或认证信息。
 * 任务十三的编排器拥有“最多两次”循环，本服务刻意不重试，避免下层绕过全局上限。
 * 本类型只在 repair 方法调用一次模型，认证由上层入口负责；本类型不负责循环、全量校验或版本持久化。
 */
public final class ItineraryRepairService implements ItineraryRepairer {
    private final PromptContextAssembler contextAssembler;
    private final ItineraryGenerator repairGenerator;

    /**
     * 创建结构化修正服务。
     *
     * @param contextAssembler 非空且与生成流程共用预算规则的组装器
     * @param repairGenerator 非空且使用修正模板的单次生成器
     * @throws NullPointerException 当任一依赖为空
     * 构造不访问模型、数据库、缓存、文件或网络，也不处理认证。
     */
    public ItineraryRepairService(
            PromptContextAssembler contextAssembler,
            ItineraryGenerator repairGenerator) {
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler");
        this.repairGenerator = Objects.requireNonNull(repairGenerator, "repairGenerator");
    }

    /**
     * 创建生产用修正服务，并让预算估算与实际模型调用使用同一个固定修正模板。
     *
     * @param properties 非空上下文预算
     * @param estimator 非空保守 Token 估算器
     * @param chatModel 非空 LangChain4j 模型
     * 构造只读取固定类路径模板，不调用模型、数据库、缓存或网络，也不处理认证。
     */
    public ItineraryRepairService(
            ContextBudgetProperties properties,
            ConservativeTokenEstimator estimator,
            ChatModel chatModel) {
        ItineraryPromptRenderer renderer = ItineraryPromptRenderer.repair();
        this.contextAssembler = new PromptContextAssembler(properties, estimator, renderer);
        this.repairGenerator = new LangChain4jItineraryGenerator(chatModel, renderer);
    }

    /**
     * 组装当前一轮的最小事实并调用模型一次。
     * 保留上一版完整行程使用的 POI 白名单，但只携带当前问题相关的路线、费用、天气和引用；
     * 不会接收或累积旧 Prompt、原始回复或 Trace。
     *
     * @param request 非空结构化修正请求
     * @param originalContext 非空可信原始上下文；其中需求必须与请求的不可变需求一致
     * @return 单次修正生成结果
     * @throws IllegalArgumentException 当请求试图扩大原始 POI/引用白名单或改变需求
     * 上下文超限由组装器在调用模型前抛出；本方法只访问配置的模型，不访问数据库、缓存、文件或其他网络；认证由上层入口负责。
     */
    @Override
    public RepairExecution repair(
            RepairRequest request,
            TravelPlanningContext originalContext) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(originalContext, "originalContext");
        if (!request.immutableRequirement().equals(
                originalContext.resolvedRequirement().requirement())) {
            throw new IllegalArgumentException("IMMUTABLE_REQUIREMENT_CHANGED");
        }

        ItineraryValidationFacts filteredFacts = filteredFacts(request, originalContext.validationFacts());
        TravelPlanningContext repairContext = new TravelPlanningContext(
                originalContext.resolvedRequirement(), filteredFacts);
        PromptContext promptContext = contextAssembler.assemble(new PromptContextInput(
                repairContext, request.issues(), null, request.previousPlan()));
        return new RepairExecution(repairGenerator.generateResult(promptContext), promptContext);
    }

    private ItineraryValidationFacts filteredFacts(
            RepairRequest request,
            ItineraryValidationFacts original) {
        Set<LocalDate> affectedDates = request.issues().stream()
                .map(issue -> issue.date()).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> affectedPoiIds = request.issues().stream()
                .map(issue -> issue.poiId()).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        boolean globalIssue = affectedDates.isEmpty() && affectedPoiIds.isEmpty();
        request.previousPlan().days().stream()
                .filter(day -> globalIssue || affectedDates.contains(day.date()))
                .flatMap(day -> day.items().stream())
                .map(item -> item.poiId())
                .forEach(affectedPoiIds::add);

        // 修复 Prompt 要求返回完整替换计划，因此白名单必须允许原计划中未受影响的 POI
        // 原样保留；详细路线、费用和天气仍只围绕当前问题裁剪。
        Set<String> allowedPoiIds = new HashSet<>(affectedPoiIds);
        request.previousPlan().days().stream()
                .flatMap(day -> day.items().stream())
                .map(item -> item.poiId())
                .forEach(allowedPoiIds::add);

        var originalPlaces = original.places().stream().collect(Collectors.toMap(
                fact -> fact.place().poiId(), Function.identity()));
        if (request.allowedPlaces().stream().anyMatch(place -> {
            ValidationPoiFact originalFact = originalPlaces.get(place.poiId());
            return originalFact == null || !originalFact.place().equals(place);
        })) {
            throw new IllegalArgumentException("REPAIR_POI_OUTSIDE_ORIGINAL_CONTEXT");
        }
        List<ValidationPoiFact> places = request.allowedPlaces().stream()
                .filter(place -> allowedPoiIds.contains(place.poiId()))
                .map(place -> {
            ValidationPoiFact fact = originalPlaces.get(place.poiId());
            if (fact == null || !fact.place().equals(place)) {
                throw new IllegalArgumentException("REPAIR_POI_OUTSIDE_ORIGINAL_CONTEXT");
            }
            return fact;
        }).toList();
        List<RouteFact> routes = original.routes().stream()
                .filter(route -> affectedPoiIds.contains(route.fromPoiId())
                        && affectedPoiIds.contains(route.toPoiId()))
                .toList();
        List<CostFact> costs = original.costs().stream()
                .filter(cost -> affectedPoiIds.contains(cost.poiId()))
                .toList();

        Set<Long> originalReferenceIds = original.knowledgeReferences().stream()
                .map(reference -> reference.chunkId()).collect(Collectors.toUnmodifiableSet());
        if (request.allowedReferences().stream()
                .anyMatch(reference -> !originalReferenceIds.contains(reference.chunkId()))) {
            throw new IllegalArgumentException("REPAIR_REFERENCE_OUTSIDE_ORIGINAL_CONTEXT");
        }
        Set<Long> affectedReferenceIds = request.previousPlan().days().stream()
                .filter(day -> globalIssue || affectedDates.contains(day.date()))
                .flatMap(day -> day.items().stream())
                .map(item -> item.cost().knowledgeChunkId())
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        var references = request.allowedReferences().stream()
                .filter(reference -> affectedReferenceIds.contains(reference.chunkId()))
                .toList();

        com.wyx.travelagent.tools.model.WeatherResult weather = null;
        if (original.weather() != null && !affectedDates.isEmpty()) {
            var forecasts = original.weather().dailyForecasts().stream()
                    .filter(forecast -> affectedDates.contains(forecast.date()))
                    .toList();
            weather = forecasts.isEmpty() ? null
                    : new com.wyx.travelagent.tools.model.WeatherResult(forecasts);
        }
        return new ItineraryValidationFacts(places, routes, costs, weather,
                original.routeBufferMinutes(), references);
    }
}
