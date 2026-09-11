package com.wyx.travelagent.planning;

import com.wyx.travelagent.planning.context.ContextBudgetExceededException;
import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.context.PromptContextInput;
import com.wyx.travelagent.planning.generation.ItineraryGenerationException;
import com.wyx.travelagent.planning.generation.ItineraryGenerationResult;
import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.PreferenceMergeResult;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.metrics.PlanMetricsCalculator;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.persistence.TravelPlanStatus;
import com.wyx.travelagent.planning.preparation.PlanningPreparationException;
import com.wyx.travelagent.planning.preparation.PlanningPreparationGateway;
import com.wyx.travelagent.planning.preparation.PlanningPreparationResult;
import com.wyx.travelagent.planning.repair.ItineraryRepairer;
import com.wyx.travelagent.planning.repair.RepairExecution;
import com.wyx.travelagent.planning.repair.RepairRequest;
import com.wyx.travelagent.planning.validation.IssueSeverity;
import com.wyx.travelagent.planning.validation.ItineraryValidator;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.planning.validation.ValidationReport;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceDetails;
import com.wyx.travelagent.trace.PlanningTraceService;
import com.wyx.travelagent.trace.TraceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 业务问题：按固定顺序编排需求、偏好、事实、生成、全量校验、最多两次修正、版本和 Trace。
 * 输入是已认证命令，输出为最终同步语义；本类型不处理 HTTP、异步队列、SSE 或第三方原始 JSON。
 * 初稿结构重试与 Java 规则修正是两个独立计数器：前者最多一次，后者最多两次，故单任务最多四次模型调用。
 * 本类型按需访问偏好、知识、工具、模型和数据库，但不读取文件或真实配置，认证由上层入口负责。任务十三只实现同步编排；本类型不负责任务十四以后的异步接口。
 */
public final class TravelPlanningOrchestrator {
    /** 初稿响应不可解析时允许的额外调用次数。 */
    public static final int MAX_INITIAL_STRUCTURE_RETRIES = 1;
    /** 可解析版本未通过全量 Java 校验时允许的修正次数。 */
    public static final int MAX_REPAIR_ATTEMPTS = 2;
    /** 任务十二固定执行的行程规则数量。 */
    public static final int EXECUTED_RULE_COUNT = 8;

    private final PreferenceMemoryService preferenceMemoryService;
    private final RequirementValidator requirementValidator;
    private final PlanningPreparationGateway preparationGateway;
    private final PromptContextAssembler contextAssembler;
    private final ItineraryGenerator generator;
    private final ItineraryRepairer repairer;
    private final ItineraryValidator itineraryValidator;
    private final TravelPlanRepository planRepository;
    private final PlanningTraceService traceService;
    private final PlanMetricsCalculator metricsCalculator;
    private final LongSupplier nanoTime;

    /**
     * 创建同步规划编排器。
     *
     * @param preferenceMemoryService 非空偏好只补全服务
     * @param requirementValidator 非空完整需求校验器
     * @param preparationGateway 非空检索、工具与内部事实准备边界
     * @param contextAssembler 非空模型调用前的唯一预算组装器
     * @param generator 非空初稿单次生成器
     * @param repairer 非空单次结构化修正端口
     * @param itineraryValidator 非空且每版必须重跑的全量 Java 校验器
     * @param planRepository 非空版本化行程仓储
     * @param traceService 非空脱敏 Trace 服务
     * @param metricsCalculator 非空客观指标计算器
     * @throws NullPointerException 当任一依赖为空
     * 构造不访问模型、数据库、缓存、文件或网络，也不处理认证。
     */
    public TravelPlanningOrchestrator(
            PreferenceMemoryService preferenceMemoryService,
            RequirementValidator requirementValidator,
            PlanningPreparationGateway preparationGateway,
            PromptContextAssembler contextAssembler,
            ItineraryGenerator generator,
            ItineraryRepairer repairer,
            ItineraryValidator itineraryValidator,
            TravelPlanRepository planRepository,
            PlanningTraceService traceService,
            PlanMetricsCalculator metricsCalculator) {
        this(preferenceMemoryService, requirementValidator, preparationGateway, contextAssembler,
                generator, repairer, itineraryValidator, planRepository, traceService,
                metricsCalculator, System::nanoTime);
    }

    TravelPlanningOrchestrator(
            PreferenceMemoryService preferenceMemoryService,
            RequirementValidator requirementValidator,
            PlanningPreparationGateway preparationGateway,
            PromptContextAssembler contextAssembler,
            ItineraryGenerator generator,
            ItineraryRepairer repairer,
            ItineraryValidator itineraryValidator,
            TravelPlanRepository planRepository,
            PlanningTraceService traceService,
            PlanMetricsCalculator metricsCalculator,
            LongSupplier nanoTime) {
        this.preferenceMemoryService = Objects.requireNonNull(preferenceMemoryService, "preferenceMemoryService");
        this.requirementValidator = Objects.requireNonNull(requirementValidator, "requirementValidator");
        this.preparationGateway = Objects.requireNonNull(preparationGateway, "preparationGateway");
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.repairer = Objects.requireNonNull(repairer, "repairer");
        this.itineraryValidator = Objects.requireNonNull(itineraryValidator, "itineraryValidator");
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        this.metricsCalculator = Objects.requireNonNull(metricsCalculator, "metricsCalculator");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /**
     * 执行一次有界同步规划。
     * 每个可解析版本都会保存行程、全量校验报告和指标；仅无 ERROR 的版本可成为 COMPLETED，WARNING 不阻塞。
     *
     * @param command 非空可信命令
     * @return 非空最终结果；已保存中间版本在后续失败时不会删除
     * 本方法按需访问偏好仓储、知识/工具、模型、行程数据库和 Trace 数据库；不访问真实配置文件，认证由上层入口负责。
     */
    public PlanningOutcome plan(TravelPlanningCommand command) {
        Objects.requireNonNull(command, "command");
        long stepStarted = nanoTime.getAsLong();
        PreferenceMergeResult merged;
        TravelRequirement requirement;
        try {
            merged = preferenceMemoryService.merge(command.userId(), command.requirementInput());
            requirement = requirementValidator.validate(merged.mergedInput());
            traceService.record(command.taskId(), null, PlanningStep.REQUIREMENT,
                    TraceStatus.SUCCEEDED, elapsedMillis(stepStarted), PlanningTraceDetails.empty());
        } catch (RuntimeException exception) {
            String code = exception instanceof RequirementValidator.ValidationException validation
                    ? validation.code() : "REQUIREMENT_PREPARATION_FAILED";
            traceService.failed(command.taskId(), null, PlanningStep.REQUIREMENT,
                    elapsedMillis(stepStarted), code);
            return failed(code);
        }

        ResolvedTravelRequirement resolved = new ResolvedTravelRequirement(
                requirement, merged.snapshot());
        PlanningPreparationResult preparation;
        stepStarted = nanoTime.getAsLong();
        try {
            preparation = preparationGateway.prepare(requirement, command.knowledgeQuery(),
                    command.toolPolicy(), command.toolPlan());
            traceService.record(command.taskId(), null, PlanningStep.PREPARATION,
                    preparation.toolSummary().weatherAvailable()
                            ? TraceStatus.SUCCEEDED : TraceStatus.DEGRADED,
                    elapsedMillis(stepStarted), PlanningTraceDetails.preparation(preparation.toolSummary()));
        } catch (PlanningPreparationException exception) {
            traceService.failed(command.taskId(), null, PlanningStep.PREPARATION,
                    elapsedMillis(stepStarted), exception.code());
            return failed(exception.code());
        }

        TravelPlanningContext planningContext = new TravelPlanningContext(resolved, preparation.facts());
        PromptContext promptContext;
        ItineraryGenerationResult generated;
        int structureRetries = 0;
        long modelDurationMs;
        stepStarted = nanoTime.getAsLong();
        try {
            promptContext = contextAssembler.assemble(new PromptContextInput(
                    planningContext, List.of(), null));
            while (true) {
                try {
                    generated = generator.generateResult(promptContext);
                    break;
                } catch (ItineraryGenerationException exception) {
                    if (!ItineraryGenerationException.INVALID_RESPONSE.equals(exception.code())
                            || structureRetries >= MAX_INITIAL_STRUCTURE_RETRIES) {
                        traceService.failed(command.taskId(), null, PlanningStep.GENERATION,
                                elapsedMillis(stepStarted), exception.code());
                        return failed(exception.code());
                    }
                    structureRetries++;
                    ValidationIssue parseIssue = new ValidationIssue(
                            "MODEL_RESPONSE_INVALID", IssueSeverity.ERROR, null, null, null,
                            "模型结构化响应无效", null, "返回单个符合 Schema 的 JSON 对象");
                    // 重试只携带稳定问题码；不把原始响应、上一轮 Prompt 或异常文本带回模型。
                    promptContext = contextAssembler.assemble(new PromptContextInput(
                            planningContext, List.of(parseIssue), null));
                }
            }
            modelDurationMs = elapsedMillis(stepStarted);
        } catch (ContextBudgetExceededException exception) {
            traceService.failed(command.taskId(), null, PlanningStep.GENERATION,
                    elapsedMillis(stepStarted), "CONTEXT_BUDGET_EXCEEDED");
            return contextBudgetExceeded();
        }

        Long planId = null;
        ItineraryPlan currentPlan = generated.plan();
        ValidationReport currentReport = null;
        PlanMetrics currentMetrics = null;
        int version = 0;
        List<String> appliedFields = appliedPreferenceFields(merged.snapshot());

        for (int repairAttempt = 0; repairAttempt <= MAX_REPAIR_ATTEMPTS; repairAttempt++) {
            version++;
            stepStarted = nanoTime.getAsLong();
            currentReport = itineraryValidator.validate(planningContext, currentPlan);
            currentMetrics = metricsCalculator.calculate(currentPlan, currentReport,
                    planningContext.validationFacts(), EXECUTED_RULE_COUNT);
            long validationDurationMs = elapsedMillis(stepStarted);
            boolean accepted = currentReport.canProceed();
            boolean exhausted = repairAttempt == MAX_REPAIR_ATTEMPTS;
            TravelPlanStatus versionStatus = accepted ? TravelPlanStatus.COMPLETED
                    : exhausted ? TravelPlanStatus.NEEDS_ADJUSTMENT : TravelPlanStatus.PLANNING;
            TravelPlanRepository.SavedVersion saved;
            try {
                if (planId == null) planId = planRepository.create(command.userId(), requirement);
                saved = planRepository.saveVersion(planId,
                        new TravelPlanRepository.VersionDraft(version, currentPlan, currentReport,
                                currentMetrics, versionStatus));
            } catch (RuntimeException exception) {
                markFailedSafely(planId);
                traceFailureSafely(command.taskId(), null, PlanningStep.VALIDATION,
                        validationDurationMs, "PLAN_PERSISTENCE_FAILED");
                return failed("PLAN_PERSISTENCE_FAILED");
            }
            PlanningStep modelStep = repairAttempt == 0 ? PlanningStep.GENERATION : PlanningStep.REPAIR;
            try {
                traceService.record(command.taskId(), saved.id(), modelStep,
                        TraceStatus.SUCCEEDED, modelDurationMs,
                        PlanningTraceDetails.model(promptContext.budgetReport(), generated.tokenUsage(),
                                appliedFields, structureRetries, repairAttempt,
                                generated.invalidReferenceCount()));
                traceService.record(command.taskId(), saved.id(), PlanningStep.VALIDATION,
                        accepted ? TraceStatus.SUCCEEDED : TraceStatus.DEGRADED, validationDurationMs,
                        PlanningTraceDetails.validation(currentMetrics, repairAttempt));

                if (accepted) {
                    traceService.record(command.taskId(), saved.id(), PlanningStep.COMPLETION,
                            TraceStatus.SUCCEEDED, 0,
                            PlanningTraceDetails.validation(currentMetrics, repairAttempt));
                    return new PlanningOutcome(PlanningOutcomeStatus.COMPLETED, planId, version,
                            currentPlan, currentReport, currentMetrics, null);
                }
                if (exhausted) {
                    traceService.record(command.taskId(), saved.id(), PlanningStep.COMPLETION,
                            TraceStatus.DEGRADED, 0,
                            PlanningTraceDetails.validation(currentMetrics, repairAttempt));
                    return new PlanningOutcome(PlanningOutcomeStatus.NEEDS_ADJUSTMENT, planId, version,
                            currentPlan, currentReport, currentMetrics, null);
                }
            } catch (RuntimeException exception) {
                markFailedSafely(planId);
                return new PlanningOutcome(PlanningOutcomeStatus.FAILED, planId, version,
                        currentPlan, currentReport, currentMetrics, "TRACE_PERSISTENCE_FAILED");
            }

            RepairRequest repairRequest = new RepairRequest(requirement, currentPlan,
                    currentReport.errors(), allowedPlaces(promptContext, planningContext),
                    allowedReferences(promptContext, planningContext));
            try {
                stepStarted = nanoTime.getAsLong();
                RepairExecution repairExecution = repairer.repair(repairRequest, planningContext);
                modelDurationMs = elapsedMillis(stepStarted);
                generated = repairExecution.result();
                promptContext = repairExecution.promptContext();
                currentPlan = generated.plan();
            } catch (ContextBudgetExceededException exception) {
                markFailedSafely(planId);
                traceFailureSafely(command.taskId(), saved.id(), PlanningStep.REPAIR,
                        elapsedMillis(stepStarted), "CONTEXT_BUDGET_EXCEEDED");
                return new PlanningOutcome(PlanningOutcomeStatus.CONTEXT_BUDGET_EXCEEDED,
                        planId, version, currentPlan, currentReport, currentMetrics,
                        "CONTEXT_BUDGET_EXCEEDED");
            } catch (ItineraryGenerationException exception) {
                markFailedSafely(planId);
                traceFailureSafely(command.taskId(), saved.id(), PlanningStep.REPAIR,
                        elapsedMillis(stepStarted), exception.code());
                return new PlanningOutcome(PlanningOutcomeStatus.FAILED, planId, version,
                        currentPlan, currentReport, currentMetrics, exception.code());
            }
        }
        throw new IllegalStateException("BOUNDED_REPAIR_LOOP_BROKEN");
    }

    private long elapsedMillis(long startedNanos) {
        long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - startedNanos);
        return Math.max(1L, elapsedNanos / 1_000_000L);
    }

    private void markFailedSafely(Long planId) {
        if (planId == null) return;
        try {
            planRepository.updateStatus(planId, TravelPlanStatus.FAILED);
        } catch (RuntimeException ignored) {
            // 已有稳定失败结果时，不让补偿写失败掩盖最初的编排错误。
        }
    }

    private void traceFailureSafely(
            long taskId,
            Long planVersionId,
            PlanningStep step,
            long durationMs,
            String code) {
        try {
            traceService.failed(taskId, planVersionId, step, durationMs, code);
        } catch (RuntimeException ignored) {
            // Trace 是审计副作用；其失败不能把已确定的稳定业务错误替换成底层异常。
        }
    }

    private static List<com.wyx.travelagent.tools.model.PlaceCandidate> allowedPlaces(
            PromptContext promptContext, TravelPlanningContext context) {
        return context.validationFacts().places().stream()
                .map(fact -> fact.place())
                .filter(place -> promptContext.allowedPoiIds().contains(place.poiId()))
                .toList();
    }

    private static List<com.wyx.travelagent.knowledge.retrieval.KnowledgeReference> allowedReferences(
            PromptContext promptContext, TravelPlanningContext context) {
        return context.validationFacts().knowledgeReferences().stream()
                .filter(reference -> promptContext.allowedKnowledgeChunkIds().contains(reference.chunkId()))
                .toList();
    }

    private static List<String> appliedPreferenceFields(AppliedPreferenceSnapshot snapshot) {
        List<String> fields = new ArrayList<>();
        if (snapshot.interestsSource() != PreferenceSource.NOT_PROVIDED) fields.add("interests");
        if (snapshot.paceSource() != PreferenceSource.NOT_PROVIDED) fields.add("pace");
        if (snapshot.travelModeSource() != PreferenceSource.NOT_PROVIDED) fields.add("travelMode");
        if (snapshot.accommodationPreferenceSource() != PreferenceSource.NOT_PROVIDED) {
            fields.add("accommodationPreference");
        }
        return List.copyOf(fields);
    }

    private static PlanningOutcome failed(String code) {
        return new PlanningOutcome(PlanningOutcomeStatus.FAILED, null, 0,
                null, null, null, code);
    }

    private static PlanningOutcome contextBudgetExceeded() {
        return new PlanningOutcome(PlanningOutcomeStatus.CONTEXT_BUDGET_EXCEEDED,
                null, 0, null, null, null, "CONTEXT_BUDGET_EXCEEDED");
    }
}
