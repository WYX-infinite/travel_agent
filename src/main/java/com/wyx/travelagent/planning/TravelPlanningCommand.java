package com.wyx.travelagent.planning;

import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;

import java.util.Objects;

/**
 * 业务问题：给同步编排器提供已认证主体、现有任务编号、原始结构化需求和受控工具计划。
 * 输入不含 API Key、聊天历史或模型原文，输出为不可变命令；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十三由编排入口构造本命令；本类型不负责认证、工具执行或模型调用。
 *
 * @param taskId 已由上层创建的正规划任务编号
 * @param userId 认证层提供的正用户编号
 * @param requirementInput 不含用户编号的原始结构化需求
 * @param knowledgeQuery 非空知识检索查询
 * @param toolPolicy 非空受控工具执行策略
 * @param toolPlan 非空白名单工具计划
 */
public record TravelPlanningCommand(
        long taskId,
        int userId,
        TravelRequirementInput requirementInput,
        String knowledgeQuery,
        ToolExecutionPolicy toolPolicy,
        ToolPlan toolPlan) {

    /** 创建可信边界命令；只校验内存值，不访问外部资源，也不处理认证。 */
    public TravelPlanningCommand {
        if (taskId <= 0 || userId <= 0) throw new IllegalArgumentException("INVALID_PLANNING_IDENTITY");
        Objects.requireNonNull(requirementInput, "requirementInput");
        if (knowledgeQuery == null || knowledgeQuery.isBlank()) {
            throw new IllegalArgumentException("knowledgeQuery");
        }
        knowledgeQuery = knowledgeQuery.strip();
        Objects.requireNonNull(toolPolicy, "toolPolicy");
        Objects.requireNonNull(toolPlan, "toolPlan");
    }
}
