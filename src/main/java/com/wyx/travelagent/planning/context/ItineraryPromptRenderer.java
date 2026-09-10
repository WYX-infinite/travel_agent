package com.wyx.travelagent.planning.context;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 业务问题：以唯一实现加载并渲染结构化行程 Prompt 包装，保证预算估算与实际模型输入完全一致。
 * 输入为 PromptContext 的固定顺序文本，输出为最终发送字符串；本类型只读取类路径模板，不访问模型、数据库、缓存或网络，不处理认证。
 * 任务十/十一内容先经任务十二组装，本渲染器供预算器和生成器共享，后续调用不得另行包装；本类型不负责裁剪、模型调用、响应解析或自动修正。
 */
public final class ItineraryPromptRenderer {
    private static final String TEMPLATE_PATH = "prompts/itinerary-generate.txt";
    private static final String CONTEXT_PLACEHOLDER = "{{PROMPT_CONTEXT}}";

    private final String template;

    /**
     * 加载并验证唯一的任务十二类路径 Prompt 模板。
     * 无输入；模板必须包含且只包含一个上下文占位符。
     *
     * @throws IllegalStateException 当模板缺失、不可读或占位符数量不是一
     * 构造只读取类路径文件，不访问模型、数据库、缓存或网络，不处理认证。
     */
    public ItineraryPromptRenderer() {
        this.template = loadTemplate();
    }

    /**
     * 渲染实际模型输入，预算器与生成器必须调用同一方法。
     *
     * @param renderedContext 非空的 PromptContext 固定顺序文本
     * @return 非空且已移除模板占位符的最终 Prompt
     * @throws NullPointerException 当上下文文本为空
     * 本方法只处理内存字符串，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public String render(String renderedContext) {
        return template.replace(CONTEXT_PLACEHOLDER,
                Objects.requireNonNull(renderedContext, "renderedContext"));
    }

    private static String loadTemplate() {
        try {
            String value = new ClassPathResource(TEMPLATE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            int first = value.indexOf(CONTEXT_PLACEHOLDER);
            if (first < 0 || first != value.lastIndexOf(CONTEXT_PLACEHOLDER)) {
                throw unavailable();
            }
            return value;
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("ITINERARY_PROMPT_TEMPLATE_UNAVAILABLE");
    }
}
