package com.wyx.travelagent.trace;

import com.wyx.travelagent.planning.generation.ModelTokenUsage;
import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.preparation.PlanningPreparationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：以字段白名单保存 Trace 客观摘要，结构上禁止完整 Prompt、模型回复、API Key、历史对话和偏好修改历史。
 * 所有文本仅允许内部固定字段名或错误码；本类型不访问外部资源或处理认证。
 * 任务十三将估算、实际用量和客观指标分开保存；本类型不负责收集 Prompt、调用模型或持久化。
 *
 * @param estimatedInputTokens Java 对最终 Prompt 的保守估算；不适用时为空
 * @param actualInputTokens 模型返回的实际输入 Token；未知时为空
 * @param actualOutputTokens 模型返回的实际输出 Token；未知时为空
 * @param contextSectionTokens 各结构化分区的估算量
 * @param appliedPreferenceFields 实际采用的偏好字段名
 * @param trimmedPreferenceFields 被预算裁剪的偏好字段名
 * @param trimmedPoiCount 被裁剪的 POI 数量
 * @param trimmedKnowledgeCount 被裁剪的知识数量
 * @param cacheHitCount 缓存命中次数
 * @param providerCallCounts 提供方类型到实际调用次数
 * @param weatherCoordinates 天气原始/实际请求坐标系摘要
 * @param structureRetryCount 初稿结构重试次数
 * @param repairAttempt Java 修正序号；非修正步骤为零
 * @param invalidReferenceCount 被安全净化的越界引用数
 * @param metrics 当前版本客观指标；非版本步骤为空
 */
public record PlanningTraceDetails(
        Integer estimatedInputTokens,
        Integer actualInputTokens,
        Integer actualOutputTokens,
        Map<String, Integer> contextSectionTokens,
        List<String> appliedPreferenceFields,
        List<String> trimmedPreferenceFields,
        int trimmedPoiCount,
        int trimmedKnowledgeCount,
        int cacheHitCount,
        Map<String, Integer> providerCallCounts,
        List<PlanningPreparationResult.WeatherCoordinateSummary> weatherCoordinates,
        int structureRetryCount,
        int repairAttempt,
        int invalidReferenceCount,
        PlanMetrics metrics) {
    private static final Set<String> CONTEXT_SECTION_FIELDS = Set.of(
            "system", "hardConstraints", "issuesAndPreviousPlan",
            "toolFacts", "memory", "knowledge", "optional");
    private static final Set<String> PREFERENCE_FIELDS = Set.of(
            "interests", "pace", "travelMode", "accommodationPreference");
    private static final Set<String> PROVIDER_FIELDS = Set.of("place", "route", "weather");

    /** 创建防御复制的字段白名单摘要；只校验内存计数，不访问外部资源，也不处理认证。 */
    public PlanningTraceDetails {
        if (estimatedInputTokens != null && estimatedInputTokens < 0
                || actualInputTokens != null && actualInputTokens < 0
                || actualOutputTokens != null && actualOutputTokens < 0
                || trimmedPoiCount < 0 || trimmedKnowledgeCount < 0 || cacheHitCount < 0
                || structureRetryCount < 0 || repairAttempt < 0 || invalidReferenceCount < 0) {
            throw new IllegalArgumentException("INVALID_TRACE_DETAILS");
        }
        contextSectionTokens = immutableMap(contextSectionTokens, CONTEXT_SECTION_FIELDS);
        appliedPreferenceFields = immutableList(appliedPreferenceFields, PREFERENCE_FIELDS,
                "appliedPreferenceFields");
        trimmedPreferenceFields = immutableList(trimmedPreferenceFields, PREFERENCE_FIELDS,
                "trimmedPreferenceFields");
        providerCallCounts = immutableMap(providerCallCounts, PROVIDER_FIELDS);
        weatherCoordinates = List.copyOf(Objects.requireNonNull(weatherCoordinates, "weatherCoordinates"));
    }

    /** @return 所有可选字段为空、计数为零的摘要；本方法不访问外部资源，也不处理认证。 */
    public static PlanningTraceDetails empty() {
        return new PlanningTraceDetails(null, null, null, Map.of(), List.of(), List.of(),
                0, 0, 0, Map.of(), List.of(), 0, 0, 0, null);
    }

    /**
     * 从一次模型调用和预算报告创建生成/修正摘要。
     *
     * @param report 非空预算报告
     * @param usage 非空实际用量或未知对象
     * @param appliedFields 非空实际采用偏好字段名
     * @param structureRetries 初稿结构重试次数
     * @param repairAttempt 修正序号
     * @param invalidReferences 净化引用数
     * @return 不含 Prompt 或模型原文的摘要
     * 本方法只处理内存摘要，不访问外部资源，也不处理认证。
     */
    public static PlanningTraceDetails model(
            com.wyx.travelagent.planning.context.ContextBudgetReport report,
            ModelTokenUsage usage,
            List<String> appliedFields,
            int structureRetries,
            int repairAttempt,
            int invalidReferences) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(usage, "usage");
        Map<String, Integer> sections = new LinkedHashMap<>();
        sections.put("system", report.systemTokens());
        sections.put("hardConstraints", report.hardConstraintTokens());
        sections.put("issuesAndPreviousPlan", report.validationIssueTokens());
        sections.put("toolFacts", report.toolFactTokens());
        sections.put("memory", report.memoryTokens());
        sections.put("knowledge", report.knowledgeTokens());
        sections.put("optional", report.optionalTokens());
        return new PlanningTraceDetails(report.totalEstimatedTokens(), usage.inputTokens(),
                usage.outputTokens(), sections, appliedFields, report.trimmedMemoryFields(),
                report.originalPoiCount() - report.retainedPoiCount(),
                report.originalKnowledgeChunkCount() - report.retainedKnowledgeChunkCount(),
                0, Map.of(), List.of(), structureRetries, repairAttempt, invalidReferences, null);
    }

    /**
     * 从工具准备摘要创建 Trace 详情。
     *
     * @param summary 非空工具客观摘要
     * @return 只含工具客观计数与坐标系的摘要
     * 本方法只处理内存对象，不访问外部资源，也不处理认证。
     */
    public static PlanningTraceDetails preparation(PlanningPreparationResult.ToolSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return new PlanningTraceDetails(null, null, null, Map.of(), List.of(), List.of(),
                0, 0, summary.cacheHitCount(), summary.providerCallCounts(),
                summary.weatherCoordinates(), 0, 0, 0, null);
    }

    /**
     * 从当前版本指标创建校验摘要。
     *
     * @param metrics 非空客观指标
     * @param repairAttempt 非负修正序号
     * @return 只含版本指标和修正序号的摘要
     * 本方法只处理内存对象，不访问外部资源，也不处理认证。
     */
    public static PlanningTraceDetails validation(PlanMetrics metrics, int repairAttempt) {
        return new PlanningTraceDetails(null, null, null, Map.of(), List.of(), List.of(),
                0, 0, 0, Map.of(), List.of(), 0, repairAttempt, 0,
                Objects.requireNonNull(metrics, "metrics"));
    }

    private static Map<String, Integer> immutableMap(
            Map<String, Integer> values,
            Set<String> allowedKeys) {
        Objects.requireNonNull(values, "values");
        if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || !allowedKeys.contains(entry.getKey())
                || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("INVALID_TRACE_MAP");
        }
        return Map.copyOf(new LinkedHashMap<>(values));
    }

    private static List<String> immutableList(
            List<String> values,
            Set<String> allowedValues,
            String field) {
        Objects.requireNonNull(values, field);
        if (values.stream().anyMatch(value -> !allowedValues.contains(value))) {
            throw new IllegalArgumentException("INVALID_TRACE_FIELD");
        }
        return List.copyOf(values);
    }
}
