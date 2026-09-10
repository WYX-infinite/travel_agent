package com.wyx.travelagent.planning.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务问题：集中保存模型窗口、输入、输出、安全余量和各上下文分区的保守预算。
 * 输入来自公开配置属性，输出为启动时已验证的不可变预算；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十事实和任务十一需求仅受预算约束，任务十二组装上下文，后续编排复用同一配置；本类型不负责官方模型能力判断、Tokenizer 或裁剪执行。
 *
 * @param modelContextWindowTokens 正数模型上下文窗口配置；仅是项目保守值
 * @param maxEstimatedInputTokens 正数最大估算输入量
 * @param reservedOutputTokens 正数预留输出量
 * @param safetyMarginTokens 正数安全余量
 * @param maxKnowledgeTokens 正数知识分区上限
 * @param maxToolFactTokens 正数工具事实分区上限
 * @param maxMemoryTokens 正数记忆分区上限
 * @param maxPoiCount 正数 POI 数量上限
 * @param maxKnowledgeChunks 正数知识切片数量上限
 */
@ConfigurationProperties("travel.ai.context")
public record ContextBudgetProperties(
        int modelContextWindowTokens,
        int maxEstimatedInputTokens,
        int reservedOutputTokens,
        int safetyMarginTokens,
        int maxKnowledgeTokens,
        int maxToolFactTokens,
        int maxMemoryTokens,
        int maxPoiCount,
        int maxKnowledgeChunks) {

    /**
     * 创建并交叉校验预算配置。
     * 所有字段必须为正，各分区不得超过输入预算，输入、输出与安全余量之和不得超过窗口。
     * 构造只校验内存数值，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws IllegalArgumentException 当任一配置违反约束，消息固定为 INVALID_CONTEXT_BUDGET
     */
    public ContextBudgetProperties {
        int[] values = {modelContextWindowTokens, maxEstimatedInputTokens, reservedOutputTokens,
                safetyMarginTokens, maxKnowledgeTokens, maxToolFactTokens, maxMemoryTokens,
                maxPoiCount, maxKnowledgeChunks};
        for (int value : values) {
            if (value <= 0) throw invalid();
        }
        long reserved = (long) maxEstimatedInputTokens + reservedOutputTokens + safetyMarginTokens;
        if (reserved > modelContextWindowTokens
                || maxKnowledgeTokens > maxEstimatedInputTokens
                || maxToolFactTokens > maxEstimatedInputTokens
                || maxMemoryTokens > maxEstimatedInputTokens) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("INVALID_CONTEXT_BUDGET");
    }
}
