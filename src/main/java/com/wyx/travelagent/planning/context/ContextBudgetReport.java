package com.wyx.travelagent.planning.context;

import java.util.List;
import java.util.Objects;

/**
 * 业务问题：记录上下文总量、各分区用量、对象保留数量和确定性裁剪原因。
 * 输入为组装过程的计数，输出为不可变审计报告；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一提供被计数对象，任务十二生成报告，后续编排可记录摘要；本类型不负责执行裁剪、调用模型或保存 Trace。
 *
 * @param totalEstimatedTokens 最终总估算输入量
 * @param systemTokens 系统和 Schema 分区估算量
 * @param hardConstraintTokens 显式硬约束分区估算量
 * @param validationIssueTokens 修正问题分区估算量
 * @param toolFactTokens 工具必要事实分区估算量
 * @param memoryTokens 记忆派生偏好分区估算量
 * @param knowledgeTokens 知识分区估算量
 * @param optionalTokens 可选说明分区估算量
 * @param originalPoiCount 原始 POI 数量
 * @param retainedPoiCount 最终保留 POI 数量
 * @param originalKnowledgeChunkCount 原始知识数量
 * @param retainedKnowledgeChunkCount 最终保留知识数量
 * @param trimmedMemoryFields 被裁剪的记忆字段名
 * @param trimReasons 其他稳定裁剪原因
 * @param withinBudget 是否同时满足总量与分区预算
 */
public record ContextBudgetReport(
        int totalEstimatedTokens,
        int systemTokens,
        int hardConstraintTokens,
        int validationIssueTokens,
        int toolFactTokens,
        int memoryTokens,
        int knowledgeTokens,
        int optionalTokens,
        int originalPoiCount,
        int retainedPoiCount,
        int originalKnowledgeChunkCount,
        int retainedKnowledgeChunkCount,
        List<String> trimmedMemoryFields,
        List<String> trimReasons,
        boolean withinBudget) {

    /**
     * 创建不可变预算报告。
     * 所有计数不得为负，保留数不得超过原始数；列表不得为空引用或含 null，并按自然顺序冻结。
     * 构造只校验和复制内存值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当列表或元素为空
     * @throws IllegalArgumentException 当计数非法
     */
    public ContextBudgetReport {
        int[] counts = {totalEstimatedTokens, systemTokens, hardConstraintTokens, validationIssueTokens,
                toolFactTokens, memoryTokens, knowledgeTokens, optionalTokens, originalPoiCount,
                retainedPoiCount, originalKnowledgeChunkCount, retainedKnowledgeChunkCount};
        for (int count : counts) if (count < 0) throw new IllegalArgumentException("negative budget count");
        if (retainedPoiCount > originalPoiCount
                || retainedKnowledgeChunkCount > originalKnowledgeChunkCount) {
            throw new IllegalArgumentException("retained count exceeds original count");
        }
        trimmedMemoryFields = immutableSorted(trimmedMemoryFields, "trimmedMemoryFields");
        trimReasons = immutableSorted(trimReasons, "trimReasons");
    }

    private static List<String> immutableSorted(List<String> source, String field) {
        Objects.requireNonNull(source, field);
        source.forEach(value -> Objects.requireNonNull(value, field + " element"));
        return source.stream().distinct().sorted().toList();
    }
}
