package com.wyx.travelagent.knowledge.index;

/**
 * 表示任务九基础向量搜索使用的查询文本。
 * <p>
 * 输入只包含非空自然语言文本，输出由 {@link KnowledgeIndex} 返回；城市过滤、相关性阈值、Top 8/Top 5
 * 业务编排与引用白名单属于任务十，不在本记录中提前实现。
 *
 * @param text 需要生成查询向量的非空文本
 */
public record KnowledgeQuery(String text) {
    /**
     * 校验基础查询不变量。
     *
     * @throws IllegalArgumentException 查询为空或仅含空白时抛出
     */
    public KnowledgeQuery {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("knowledge query text must not be blank");
        }
        text = text.strip();
    }
}
