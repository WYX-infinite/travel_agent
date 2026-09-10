package com.wyx.travelagent.planning.generation;

import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.model.ItineraryPlan;

/**
 * 业务问题：以预算内 PromptContext 为唯一边界生成类型化行程草稿。
 * 输入为任务十二已经净化的上下文，输出为不可变 ItineraryPlan；实现可调用模型，但不访问数据库、缓存或其他业务来源，不处理认证。
 * 任务十/十一数据先经任务十二组装，后续调用再校验或修正结果；本接口不负责检索、上下文裁剪、重试或行程规则校验。
 */
public interface ItineraryGenerator {

    /**
     * 生成一次结构化行程草稿。
     *
     * @param context 非空且预算报告通过的唯一模型上下文
     * @return 非空、严格解析且仅引用上下文白名单的行程计划
     * @throws NullPointerException 当上下文为空
     * @throws ItineraryGenerationException 当模型调用失败或响应不符合结构契约
     * 实现允许调用一次模型，不访问数据库、缓存或文件中的业务数据，不处理认证。
     */
    ItineraryPlan generate(PromptContext context);
}
