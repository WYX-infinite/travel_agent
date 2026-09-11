package com.wyx.travelagent.planning.preparation;

import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;

/**
 * 业务问题：隔离知识检索、受控工具执行和事实转换，使编排器只接收内部可信对象。
 * 实现可访问知识索引、地图、天气与缓存，但不得调用行程模型或处理认证。
 * 输入为校验后的需求与白名单工具计划，输出为内部事实和脱敏摘要；接口本身不访问外部资源。任务十三只定义事实准备边界；本类型不负责认证、生成或校验行程。
 */
@FunctionalInterface
public interface PlanningPreparationGateway {

    /**
     * 准备生成与校验共用的事实快照。
     *
     * @param requirement 已合并偏好并校验的不可变需求
     * @param knowledgeQuery 非空检索查询
     * @param policy 非空受控工具策略
     * @param plan 非空白名单工具计划
     * @return 非空事实与脱敏工具摘要
     * @throws PlanningPreparationException 当 POI 等必需来源不可用
     * 实现按需访问知识、工具、缓存和网络，不得传回第三方原始 JSON、API Key 或完整响应；认证由上层入口负责。
     */
    PlanningPreparationResult prepare(
            TravelRequirement requirement,
            String knowledgeQuery,
            ToolExecutionPolicy policy,
            ToolPlan plan);
}
