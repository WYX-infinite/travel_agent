package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.generation.ItineraryGenerationResult;

import java.util.Objects;

/**
 * 业务问题：把一次修正的模型结果与该轮实际预算上下文绑定，供编排器输出准确 Trace。
 * 输入为单次修正结果和非空上下文，输出为不可变执行快照；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三只用它传递当前轮元数据；本类型不负责重试、校验、持久化或保存 Prompt 原文。
 *
 * @param result 非空单次修正结果
 * @param promptContext 非空且仅属于本轮的预算上下文
 */
public record RepairExecution(
        ItineraryGenerationResult result,
        PromptContext promptContext) {

    /** 创建修正执行快照；只做内存校验，不访问外部资源，也不处理认证。 */
    public RepairExecution {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(promptContext, "promptContext");
    }
}
