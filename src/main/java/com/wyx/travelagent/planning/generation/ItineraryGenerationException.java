package com.wyx.travelagent.planning.generation;

import java.util.Objects;

/**
 * 业务问题：用稳定且脱敏的错误码表示结构化行程生成失败。
 * 输入为内部失败分类，输出为不携带模型原文或底层异常的运行时异常；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十二生成适配器抛出本异常，后续编排可据错误码决定响应；本类型不负责日志、重试、解析或自动修正。
 */
public final class ItineraryGenerationException extends RuntimeException {
    /** 模型调用失败或没有返回文本。 */
    public static final String GENERATION_FAILED = "ITINERARY_GENERATION_FAILED";
    /** 模型文本不满足严格 JSON、领域不变量或上下文白名单。 */
    public static final String INVALID_RESPONSE = "INVALID_ITINERARY_RESPONSE";

    private final String code;

    /**
     * 创建只含允许错误码的脱敏异常。
     *
     * @param code 必须是本类型声明的稳定错误码之一，不得为空
     * @throws NullPointerException 当错误码为空
     * @throws IllegalArgumentException 当错误码不在允许集合内
     * 构造不访问模型、数据库、缓存、文件或网络，不处理认证，且不保存底层异常或模型原文。
     */
    public ItineraryGenerationException(String code) {
        super(validate(code));
        this.code = code;
    }

    /**
     * 返回可供后续调用映射的稳定业务错误码。
     *
     * @return 非空且不包含模型内容的稳定错误码
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public String code() {
        return code;
    }

    private static String validate(String code) {
        Objects.requireNonNull(code, "code");
        if (!GENERATION_FAILED.equals(code) && !INVALID_RESPONSE.equals(code)) {
            throw new IllegalArgumentException("unsupported itinerary generation error code");
        }
        return code;
    }
}
