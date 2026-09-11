package com.wyx.travelagent.planning.generation;

/**
 * 业务问题：区分模型提供的实际 Token 用量与供应商未返回元数据的未知状态。
 * 输入只接受非负计数或成对的 {@code null}，输出为不可变用量；本类型不估算 Token，也不访问外部资源或认证信息。
 * 任务十三将本对象与 Java 保守估算值分别写入 Trace，禁止用估算值冒充供应商实际值。
 * 本类型不负责估算、模型调用或补造未知计数。
 *
 * @param inputTokens 模型响应元数据中的实际输入 Token；未知时为 {@code null}
 * @param outputTokens 模型响应元数据中的实际输出 Token；未知时为 {@code null}
 */
public record ModelTokenUsage(Integer inputTokens, Integer outputTokens) {

    /**
     * 创建实际用量或明确未知值。
     *
     * @throws IllegalArgumentException 当只缺一项或任一计数为负
     * 构造不访问模型、数据库、缓存、文件或网络，也不处理认证。
     */
    public ModelTokenUsage {
        if ((inputTokens == null) != (outputTokens == null)
                || inputTokens != null && (inputTokens < 0 || outputTokens < 0)) {
            throw new IllegalArgumentException("INVALID_MODEL_TOKEN_USAGE");
        }
    }

    /** @return 两项实际计数均存在时为 {@code true}；本方法不访问外部资源，也不处理认证。 */
    public boolean known() {
        return inputTokens != null;
    }

    /** @return 供应商未提供元数据时使用的明确未知对象；本方法不访问外部资源，也不处理认证。 */
    public static ModelTokenUsage unknown() {
        return new ModelTokenUsage(null, null);
    }
}
