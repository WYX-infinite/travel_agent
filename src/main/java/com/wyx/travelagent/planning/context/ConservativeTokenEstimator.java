package com.wyx.travelagent.planning.context;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 业务问题：在没有模型官方 Tokenizer 时提供确定性、上偏的输入量估算。
 * 输入为最终待发送文本，输出为整数估算量；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一对象先由任务十二序列化后再估算，后续编排可记录结果；本类型不负责精确复刻供应商分词、调用模型或裁剪对象。
 */
public final class ConservativeTokenEstimator {
    private static final int FIXED_STRUCTURE_OVERHEAD = 8;

    /**
     * 按 UTF-8 字节和固定结构开销估算输入量。
     * 公式为 ceil(UTF-8字节数/3) + 换行数 + 8；JSON 转义后的实际文本应直接传入，因此转义和 Emoji 字节会被计入。
     *
     * @param text 非空最终文本；允许显式空串
     * @return 非负且对同一输入恒定的保守估算量
     * @throws NullPointerException 当文本为 {@code null}
     * 本方法不是官方 Tokenizer，只做本地确定性估算，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public int estimate(String text) {
        Objects.requireNonNull(text, "text");
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        long newlines = text.chars().filter(character -> character == '\n').count();
        return Math.toIntExact((bytes + 2L) / 3L + newlines + FIXED_STRUCTURE_OVERHEAD);
    }
}
