package com.wyx.travelagent.planning.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.model.CostFact;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.planning.validation.ValidationReport;
import com.wyx.travelagent.tools.model.WeatherResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 业务问题：把内部需求、问题、工具事实、偏好和知识转换为预算内、确定顺序、可原子裁剪的 PromptContext。
 * 输入仅为 PromptContextInput，输出为预算内上下文或稳定超限异常；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一对象必须先进入内部上下文，任务十二在模型调用前执行组装，后续任务十三可复用问题分区；本类型不负责调用模型、读取原始第三方响应或自动修正。
 */
public final class PromptContextAssembler {
    private static final String SYSTEM_AND_SCHEMA = """
            你是结构化行程草稿生成器，只能输出单个 JSON 对象。
            只能使用 TOOL_FACTS 中允许的 poiId；每项必须返回真实 poiId，禁止用自造名称代替。
            日期使用 ISO LocalDate，时间使用 ISO LocalTime，金额使用非负十进制。
            费用状态为 KNOWN 或 MISSING；KNOWN 的 evidenceType 只能是 FACT、DOCUMENT、AI_ESTIMATE。
            FACT 只能来自工具费用事实；DOCUMENT 必须引用允许的 knowledgeChunkId；AI_ESTIMATE 必须明确为估算。
            不得编造营业时间、实时票价、评分、排名、评论或安全事实；资料不足时使用 MISSING。
            禁止 Markdown 代码围栏、额外自然语言、Prompt、系统指令、秘密、第三方原始 JSON、聊天历史或 Trace。
            JSON Schema:
            {"destinationCity":"string","startDate":"yyyy-MM-dd","endDate":"yyyy-MM-dd",
             "days":[{"date":"yyyy-MM-dd","items":[{"startTime":"HH:mm","endTime":"HH:mm",
             "poiId":"string","name":"string","description":"string|null",
             "cost":{"status":"KNOWN|MISSING","minimum":"decimal|null","maximum":"decimal|null",
             "evidenceType":"FACT|DOCUMENT|AI_ESTIMATE|null","knowledgeChunkId":"integer|null"},
             "travelModeFromPrevious":"WALKING|TRANSIT|TAXI|DRIVING|null"}]}],
             "estimatedTotalCost":"decimal"}
            """.strip();

    private final ContextBudgetProperties properties;
    private final ConservativeTokenEstimator estimator;
    private final ItineraryPromptRenderer promptRenderer;
    private final ObjectMapper objectMapper;

    /**
     * 创建确定性上下文组装器。
     *
     * @param properties 非空且已通过交叉校验的预算配置
     * @param estimator 非空本地保守估算器
     * @throws NullPointerException 当任一依赖为空
     * 构造会读取任务十二类路径模板并准备内存 JSON 编码器；不访问模型、数据库、缓存或网络，不处理认证。
     */
    public PromptContextAssembler(ContextBudgetProperties properties, ConservativeTokenEstimator estimator) {
        this(properties, estimator, new ItineraryPromptRenderer());
    }

    PromptContextAssembler(
            ContextBudgetProperties properties,
            ConservativeTokenEstimator estimator,
            ItineraryPromptRenderer promptRenderer) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 按固定优先级组装并原子裁剪上下文。
     * 系统 Schema、显式硬约束、修正问题和 required POI 等必要事实不会被裁剪；可选说明、记忆、知识和非必需 POI 依次裁剪。
     *
     * @param input 非空内部组装输入；空问题列表和缺失可选说明均被明确支持
     * @return 满足总预算、分区预算与数量上限的不可变 PromptContext
     * @throws NullPointerException 当输入为空
     * @throws ContextBudgetExceededException 当必要内容自身超限，稳定错误码为 CONTEXT_BUDGET_EXCEEDED
     * 本方法不调用模型，也不访问数据库、缓存、文件或网络，不处理认证；结构化对象只按完整元素删除，绝不中途截断。
     */
    public PromptContext assemble(PromptContextInput input) {
        Objects.requireNonNull(input, "input");
        TravelRequirement requirement = input.planningContext().resolvedRequirement().requirement();
        AppliedPreferenceSnapshot snapshot =
                input.planningContext().resolvedRequirement().appliedPreferenceSnapshot();
        ItineraryValidationFacts facts = input.planningContext().validationFacts();

        String hardConstraints = json(hardConstraint(requirement, snapshot));
        List<String> validationEntries = new ValidationReport(input.validationIssues()).issues().stream()
                .map(this::json)
                .toList();
        List<String> trimReasons = new ArrayList<>();
        List<String> trimmedMemoryFields = new ArrayList<>();

        List<ValidationPoiFact> retainedPlaces = retainInitialPlaces(facts.places(), trimReasons);
        List<String> toolEntries = toolEntries(facts, retainedPlaces);
        while (estimateListSection("TOOL_FACTS", toolEntries) > properties.maxToolFactTokens()) {
            if (!removeLowestPriorityOptionalPoi(retainedPlaces)) throw new ContextBudgetExceededException();
            addReason(trimReasons, "LOW_PRIORITY_POI_REMOVED");
            toolEntries = toolEntries(facts, retainedPlaces);
        }

        List<MemoryEntry> retainedMemory = memoryEntries(snapshot);
        while (estimateListSection("APPLIED_MEMORY", memoryJson(retainedMemory))
                > properties.maxMemoryTokens()) {
            if (retainedMemory.isEmpty()) throw new ContextBudgetExceededException();
            MemoryEntry removed = retainedMemory.removeLast();
            trimmedMemoryFields.add(removed.field());
            addReason(trimReasons, "MEMORY_FIELD_REMOVED");
        }
        List<String> memoryEntries = memoryJson(retainedMemory);

        List<KnowledgeReference> retainedKnowledge = new ArrayList<>(
                facts.knowledgeReferences().stream().limit(properties.maxKnowledgeChunks()).toList());
        if (facts.knowledgeReferences().size() > retainedKnowledge.size()) {
            addReason(trimReasons, "LOW_RELEVANCE_KNOWLEDGE_REMOVED");
        }
        List<String> knowledgeEntries = knowledgeJson(retainedKnowledge);
        while (estimateListSection("KNOWLEDGE_REFERENCES", knowledgeEntries)
                > properties.maxKnowledgeTokens()) {
            if (retainedKnowledge.isEmpty()) throw new ContextBudgetExceededException();
            retainedKnowledge.removeLast();
            addReason(trimReasons, "LOW_RELEVANCE_KNOWLEDGE_REMOVED");
            knowledgeEntries = knowledgeJson(retainedKnowledge);
        }

        List<String> optionalEntries = input.optionalDescription() == null
                ? new ArrayList<>()
                : new ArrayList<>(List.of(json(new OptionalDescriptionDto(input.optionalDescription()))));

        int total = total(SYSTEM_AND_SCHEMA, hardConstraints, validationEntries, toolEntries,
                memoryEntries, knowledgeEntries, optionalEntries);
        while (total > properties.maxEstimatedInputTokens()) {
            if (!optionalEntries.isEmpty()) {
                optionalEntries.clear();
                addReason(trimReasons, "OPTIONAL_DESCRIPTION_REMOVED");
            } else if (!retainedMemory.isEmpty()) {
                MemoryEntry removed = retainedMemory.removeLast();
                trimmedMemoryFields.add(removed.field());
                addReason(trimReasons, "MEMORY_FIELD_REMOVED");
                memoryEntries = memoryJson(retainedMemory);
            } else if (!retainedKnowledge.isEmpty()) {
                retainedKnowledge.removeLast();
                addReason(trimReasons, "LOW_RELEVANCE_KNOWLEDGE_REMOVED");
                knowledgeEntries = knowledgeJson(retainedKnowledge);
            } else if (removeLowestPriorityOptionalPoi(retainedPlaces)) {
                addReason(trimReasons, "LOW_PRIORITY_POI_REMOVED");
                toolEntries = toolEntries(facts, retainedPlaces);
            } else {
                throw new ContextBudgetExceededException();
            }
            total = total(SYSTEM_AND_SCHEMA, hardConstraints, validationEntries, toolEntries,
                    memoryEntries, knowledgeEntries, optionalEntries);
        }

        int systemTokens = estimateSingleSection("SYSTEM_AND_SCHEMA", SYSTEM_AND_SCHEMA);
        int hardTokens = estimateSingleSection("HARD_CONSTRAINTS", hardConstraints);
        int issueTokens = estimateListSection("VALIDATION_ISSUES", validationEntries);
        int toolTokens = estimateListSection("TOOL_FACTS", toolEntries);
        int memoryTokens = estimateListSection("APPLIED_MEMORY", memoryEntries);
        int knowledgeTokens = estimateListSection("KNOWLEDGE_REFERENCES", knowledgeEntries);
        int optionalTokens = estimateListSection("OPTIONAL_DESCRIPTION", optionalEntries);
        boolean within = total <= properties.maxEstimatedInputTokens()
                && toolTokens <= properties.maxToolFactTokens()
                && memoryTokens <= properties.maxMemoryTokens()
                && knowledgeTokens <= properties.maxKnowledgeTokens();
        if (!within) throw new ContextBudgetExceededException();

        ContextBudgetReport report = new ContextBudgetReport(
                total, systemTokens, hardTokens, issueTokens, toolTokens, memoryTokens,
                knowledgeTokens, optionalTokens, facts.places().size(), retainedPlaces.size(),
                facts.knowledgeReferences().size(), retainedKnowledge.size(),
                trimmedMemoryFields, trimReasons, true);
        Set<String> allowedPoiIds = retainedPlaces.stream()
                .map(place -> place.place().poiId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> allowedChunkIds = retainedKnowledge.stream()
                .map(KnowledgeReference::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new PromptContext(SYSTEM_AND_SCHEMA, hardConstraints, validationEntries, toolEntries,
                memoryEntries, knowledgeEntries, optionalEntries, report,
                Collections.unmodifiableSet(allowedPoiIds), Collections.unmodifiableSet(allowedChunkIds));
    }

    private HardConstraintDto hardConstraint(
            TravelRequirement requirement, AppliedPreferenceSnapshot snapshot) {
        return new HardConstraintDto(
                requirement.destinationCity(), requirement.startDate().toString(),
                requirement.endDate().toString(), requirement.travelers(),
                requirement.totalBudget().toPlainString(), requirement.companionType().name(),
                snapshot.interestsSource() == PreferenceSource.EXPLICIT_REQUEST
                        ? requirement.interests().stream().map(Enum::name).sorted().toList() : null,
                snapshot.paceSource() == PreferenceSource.EXPLICIT_REQUEST && requirement.pace() != null
                        ? requirement.pace().name() : null,
                snapshot.accommodationPreferenceSource() == PreferenceSource.EXPLICIT_REQUEST
                        ? requirement.accommodationAddress() : null,
                snapshot.travelModeSource() == PreferenceSource.EXPLICIT_REQUEST
                        && requirement.travelMode() != null ? requirement.travelMode().name() : null,
                requirement.specialRequirements());
    }

    private List<ValidationPoiFact> retainInitialPlaces(
            List<ValidationPoiFact> places, List<String> trimReasons) {
        long requiredCount = places.stream().filter(ValidationPoiFact::required).count();
        if (requiredCount > properties.maxPoiCount()) throw new ContextBudgetExceededException();
        List<ValidationPoiFact> retained = new ArrayList<>();
        places.stream().filter(ValidationPoiFact::required).forEach(retained::add);
        places.stream().filter(place -> !place.required())
                .limit(properties.maxPoiCount() - requiredCount)
                .forEach(retained::add);
        retained.sort(Comparator.comparing(ValidationPoiFact::required).reversed()
                .thenComparingInt(ValidationPoiFact::priority)
                .thenComparing(place -> place.place().poiId()));
        if (retained.size() < places.size()) addReason(trimReasons, "LOW_PRIORITY_POI_REMOVED");
        return retained;
    }

    private boolean removeLowestPriorityOptionalPoi(List<ValidationPoiFact> retained) {
        for (int index = retained.size() - 1; index >= 0; index--) {
            if (!retained.get(index).required()) {
                retained.remove(index);
                return true;
            }
        }
        return false;
    }

    private List<String> toolEntries(ItineraryValidationFacts facts, List<ValidationPoiFact> places) {
        Set<String> retainedIds = places.stream().map(place -> place.place().poiId())
                .collect(Collectors.toUnmodifiableSet());
        List<String> entries = new ArrayList<>();
        for (ValidationPoiFact place : places) {
            entries.add(json(new PoiFactDto("POI", place.place().poiId(), place.place().name(),
                    place.place().address(), place.priority(), place.required(), place.outdoor(),
                    place.openingHours().reliable(),
                    place.openingHours().opensAt() == null ? null : place.openingHours().opensAt().toString(),
                    place.openingHours().closesAt() == null ? null : place.openingHours().closesAt().toString())));
        }
        facts.routes().stream()
                .filter(route -> retainedIds.contains(route.fromPoiId()) && retainedIds.contains(route.toPoiId()))
                .map(this::routeJson)
                .forEach(entries::add);
        facts.costs().stream()
                .filter(cost -> retainedIds.contains(cost.poiId()))
                .map(this::costJson)
                .forEach(entries::add);
        if (facts.weather() != null) {
            facts.weather().dailyForecasts().stream()
                    .map(this::weatherJson)
                    .forEach(entries::add);
        }
        return List.copyOf(entries);
    }

    private String routeJson(RouteFact fact) {
        return json(new RouteFactDto("ROUTE", fact.fromPoiId(), fact.toPoiId(),
                fact.segment().mode().name(), fact.segment().distanceMeters(),
                fact.segment().durationMinutes(), fact.segment().verified()));
    }

    private String costJson(CostFact fact) {
        return json(new CostFactDto("COST", fact.poiId(),
                fact.estimate().minimum().toPlainString(), fact.estimate().maximum().toPlainString(),
                fact.estimate().evidenceType().name(), fact.estimate().knowledgeChunkId()));
    }

    private String weatherJson(WeatherResult.DailyForecast forecast) {
        return json(new WeatherFactDto("WEATHER", forecast.date().toString(),
                forecast.minimumTemperatureCelsius(), forecast.maximumTemperatureCelsius(),
                forecast.precipitationProbabilityPercent(), forecast.precipitationMillimeters()));
    }

    private List<MemoryEntry> memoryEntries(AppliedPreferenceSnapshot snapshot) {
        List<MemoryEntry> entries = new ArrayList<>();
        if (snapshot.accommodationPreferenceSource() == PreferenceSource.SAVED_PREFERENCE
                && snapshot.accommodationPreference() != null) {
            entries.add(new MemoryEntry("accommodationPreference", snapshot.accommodationPreference()));
        }
        if (snapshot.interestsSource() == PreferenceSource.SAVED_PREFERENCE
                && !snapshot.interests().isEmpty()) {
            entries.add(new MemoryEntry("interests",
                    snapshot.interests().stream().map(Enum::name).sorted().collect(Collectors.joining(","))));
        }
        if (snapshot.paceSource() == PreferenceSource.SAVED_PREFERENCE && snapshot.pace() != null) {
            entries.add(new MemoryEntry("pace", snapshot.pace().name()));
        }
        if (snapshot.travelModeSource() == PreferenceSource.SAVED_PREFERENCE
                && snapshot.travelMode() != null) {
            entries.add(new MemoryEntry("travelMode", snapshot.travelMode().name()));
        }
        entries.sort(java.util.Comparator.comparing(MemoryEntry::field));
        return entries;
    }

    private List<String> memoryJson(List<MemoryEntry> entries) {
        return entries.stream()
                .map(entry -> json(new MemoryDto(entry.field(), entry.value(), "SAVED_PREFERENCE")))
                .toList();
    }

    private List<String> knowledgeJson(List<KnowledgeReference> references) {
        return references.stream().map(reference -> json(new KnowledgeDto(
                reference.chunkId(), reference.title(), reference.publisher(),
                reference.sourceUrl().toString(), reference.content(), reference.score()))).toList();
    }

    private int total(String system, String hard, List<String> issues, List<String> tools,
                      List<String> memory, List<String> knowledge, List<String> optional) {
        String renderedContext = PromptContext.renderSections(
                system, hard, issues, tools, memory, knowledge, optional);
        return estimator.estimate(promptRenderer.render(renderedContext));
    }

    private int estimateSingleSection(String name, String value) {
        return estimator.estimate("[" + name + "]\n" + value + "\n");
    }

    private int estimateListSection(String name, List<String> entries) {
        return estimator.estimate("[" + name + "]\n" + String.join("\n", entries)
                + (entries.isEmpty() ? "" : "\n"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CONTEXT_ASSEMBLY_FAILED", exception);
        }
    }

    private static void addReason(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    private record MemoryEntry(String field, String value) {
    }

    private record HardConstraintDto(
            String destinationCity, String startDate, String endDate, int travelers,
            String totalBudget, String companionType, List<String> interests, String pace,
            String accommodationAddress, String travelMode, String specialRequirements) {
    }

    private record PoiFactDto(
            String type, String poiId, String name, String address, int priority, boolean required,
            boolean outdoor, boolean openingHoursReliable, String opensAt, String closesAt) {
    }

    private record RouteFactDto(
            String type, String fromPoiId, String toPoiId, String mode,
            Long distanceMeters, Long durationMinutes, boolean verified) {
    }

    private record CostFactDto(
            String type, String poiId, String minimum, String maximum,
            String evidenceType, Long knowledgeChunkId) {
    }

    private record WeatherFactDto(
            String type, String date, double minimumTemperatureCelsius,
            double maximumTemperatureCelsius, int precipitationProbabilityPercent,
            double precipitationMillimeters) {
    }

    private record MemoryDto(String field, String value, String source) {
    }

    private record KnowledgeDto(
            Long chunkId, String title, String publisher, String sourceUrl, String content, double score) {
    }

    private record OptionalDescriptionDto(String description) {
    }
}
