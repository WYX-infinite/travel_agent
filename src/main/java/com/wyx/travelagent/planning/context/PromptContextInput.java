package com.wyx.travelagent.planning.context;

import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.validation.ValidationIssue;

import java.util.List;
import java.util.Objects;

/**
 * 业务问题：限定上下文组装器只接收内部规划对象、结构化问题与单段可选说明。
 * 输入不包含第三方 JSON、聊天历史、Trace、旧 Prompt 或模型原文，输出为不可变组装请求；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一信息必须先进入 TravelPlanningContext，任务十二消费本输入，后续任务十三可填问题列表；本类型不负责组装、生成或自动修正。
 *
 * @param planningContext 非空内部规划上下文
 * @param validationIssues 非空且可为空的结构化问题列表
 * @param optionalDescription 可缺失的可选解释文字；{@code null} 表示没有
 * @param previousPlan 可缺失的上一版类型化行程；普通生成时为 {@code null}
 */
public record PromptContextInput(
        TravelPlanningContext planningContext,
        List<ValidationIssue> validationIssues,
        String optionalDescription,
        ItineraryPlan previousPlan) {

    /**
     * 创建不带上一版方案的普通生成输入，保持任务十二调用兼容。
     *
     * @param planningContext 非空内部规划上下文
     * @param validationIssues 非空结构化问题列表
     * @param optionalDescription 可缺失的可选说明
     * 本构造器只委托规范构造器，不访问任何外部资源，也不处理认证。
     */
    public PromptContextInput(
            TravelPlanningContext planningContext,
            List<ValidationIssue> validationIssues,
            String optionalDescription) {
        this(planningContext, validationIssues, optionalDescription, null);
    }

    /**
     * 创建不可变组装输入。
     * 上下文与问题列表不得为空，问题元素不得为空；可选说明会去除首尾空白，空白归一为 null。
     * 构造只处理内存对象，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当上下文、问题列表或问题元素为空
     */
    public PromptContextInput {
        Objects.requireNonNull(planningContext, "planningContext");
        Objects.requireNonNull(validationIssues, "validationIssues");
        validationIssues.forEach(issue -> Objects.requireNonNull(issue, "validationIssue"));
        validationIssues = List.copyOf(validationIssues);
        if (optionalDescription != null) {
            optionalDescription = optionalDescription.strip();
            if (optionalDescription.isEmpty()) optionalDescription = null;
        }
    }
}
