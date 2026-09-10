package com.wyx.travelagent.planning.context;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：保存已经预算、裁剪和净化的结构化 Prompt 分区，作为生成器唯一输入。
 * 输入是完整系统/硬约束分区及原子 JSON 条目，输出为不可变可渲染上下文；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一对象只能经任务十二组装器转换为本类型，后续调用只消费本类型；本类型不负责再次读取领域对象、调用模型或自动修正。
 *
 * @param systemSection 非空系统约束与 JSON Schema
 * @param hardConstraintSection 非空显式硬约束 JSON
 * @param validationIssueEntries 不可变修正问题 JSON 对象
 * @param toolFactEntries 不可变 POI、路线、天气和费用事实 JSON 对象
 * @param memoryEntries 不可变记忆派生偏好 JSON 对象
 * @param knowledgeEntries 不可变知识引用 JSON 对象
 * @param optionalEntries 不可变可选说明 JSON 对象
 * @param budgetReport 非空且必须满足预算的报告
 * @param allowedPoiIds 最终允许模型使用的 POI ID
 * @param allowedKnowledgeChunkIds 最终允许模型引用的知识切片 ID
 */
public record PromptContext(
        String systemSection,
        String hardConstraintSection,
        List<String> validationIssueEntries,
        List<String> toolFactEntries,
        List<String> memoryEntries,
        List<String> knowledgeEntries,
        List<String> optionalEntries,
        ContextBudgetReport budgetReport,
        Set<String> allowedPoiIds,
        Set<Long> allowedKnowledgeChunkIds) {

    /**
     * 创建预算内不可变 PromptContext。
     * 文本分区必须非空，列表、报告和白名单不得为空引用；所有 JSON 条目按调用方已确定顺序防御复制。
     * 构造不重新估算；不访问模型、数据库、缓存、文件或网络，不处理认证；报告超预算时稳定拒绝。
     *
     * @throws NullPointerException 当必要分区、列表、报告或白名单为空
     * @throws IllegalArgumentException 当必要文本为空白或报告标记超预算
     */
    public PromptContext {
        systemSection = requireText(systemSection, "systemSection");
        hardConstraintSection = requireText(hardConstraintSection, "hardConstraintSection");
        validationIssueEntries = immutable(validationIssueEntries, "validationIssueEntries");
        toolFactEntries = immutable(toolFactEntries, "toolFactEntries");
        memoryEntries = immutable(memoryEntries, "memoryEntries");
        knowledgeEntries = immutable(knowledgeEntries, "knowledgeEntries");
        optionalEntries = immutable(optionalEntries, "optionalEntries");
        Objects.requireNonNull(budgetReport, "budgetReport");
        if (!budgetReport.withinBudget()) throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        allowedPoiIds = immutableSet(allowedPoiIds, "allowedPoiIds");
        allowedKnowledgeChunkIds = immutableSet(
                allowedKnowledgeChunkIds, "allowedKnowledgeChunkIds");
    }

    /**
     * 渲染生成器唯一允许发送的完整 Prompt。
     *
     * @return 按固定分区顺序组成的文本；每个列表元素保持完整，不从 JSON 中间截断
     * 无输入且不抛业务异常；本方法只拼接已净化内存字符串，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public String render() {
        return renderSections(systemSection, hardConstraintSection, validationIssueEntries,
                toolFactEntries, memoryEntries, knowledgeEntries, optionalEntries);
    }

    static String renderSections(
            String systemSection,
            String hardConstraintSection,
            List<String> validationIssueEntries,
            List<String> toolFactEntries,
            List<String> memoryEntries,
            List<String> knowledgeEntries,
            List<String> optionalEntries) {
        StringBuilder prompt = new StringBuilder();
        appendSingle(prompt, "SYSTEM_AND_SCHEMA", systemSection);
        appendSingle(prompt, "HARD_CONSTRAINTS", hardConstraintSection);
        appendList(prompt, "VALIDATION_ISSUES", validationIssueEntries);
        appendList(prompt, "TOOL_FACTS", toolFactEntries);
        appendList(prompt, "APPLIED_MEMORY", memoryEntries);
        appendList(prompt, "KNOWLEDGE_REFERENCES", knowledgeEntries);
        appendList(prompt, "OPTIONAL_DESCRIPTION", optionalEntries);
        return prompt.toString();
    }

    private static void appendSingle(StringBuilder prompt, String name, String value) {
        prompt.append('[').append(name).append("]\n").append(value).append('\n');
    }

    private static void appendList(StringBuilder prompt, String name, List<String> values) {
        prompt.append('[').append(name).append("]\n");
        values.forEach(value -> prompt.append(value).append('\n'));
    }

    private static List<String> immutable(List<String> source, String field) {
        Objects.requireNonNull(source, field);
        source.forEach(value -> requireText(value, field + " element"));
        return List.copyOf(source);
    }

    private static <T> Set<T> immutableSet(Set<T> source, String field) {
        Objects.requireNonNull(source, field);
        source.forEach(value -> Objects.requireNonNull(value, field + " element"));
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field);
        return value;
    }
}
