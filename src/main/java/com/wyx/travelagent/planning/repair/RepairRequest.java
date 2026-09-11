package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.tools.model.PlaceCandidate;

import java.util.List;
import java.util.Objects;

/**
 * 业务问题：把一次修正严格限制为不可变需求、上一版结构化方案、当前问题和当前白名单事实。
 * 输入明确排除旧 Prompt、模型原文、完整 Trace 与历史对话，输出为不可变修正请求；本类型不访问外部资源，也不处理认证。
 * 任务十三每轮重新创建本对象，后续轮次只引用紧邻上一版，因而不会累积自然语言上下文。
 * 本类型不负责模型调用、Java 校验、重试循环或持久化。
 *
 * @param immutableRequirement 本任务不可由模型修改的强类型需求
 * @param previousPlan 紧邻上一版可解析行程，不得是模型原文
 * @param issues 针对上一版的当前全量 Java 校验问题
 * @param allowedPlaces 当前预算内允许使用的 POI 白名单
 * @param allowedReferences 当前预算内允许引用的知识切片白名单
 */
public record RepairRequest(
        TravelRequirement immutableRequirement,
        ItineraryPlan previousPlan,
        List<ValidationIssue> issues,
        List<PlaceCandidate> allowedPlaces,
        List<KnowledgeReference> allowedReferences) {

    /**
     * 创建防御复制的结构化修正请求。
     *
     * @throws NullPointerException 当任一必需对象、列表或列表元素为空
     * @throws IllegalArgumentException 当问题列表为空，因无问题时不应调用修正模型
     * 构造只复制内存数据，不访问模型、数据库、缓存、文件或网络，也不处理认证。
     */
    public RepairRequest {
        Objects.requireNonNull(immutableRequirement, "immutableRequirement");
        Objects.requireNonNull(previousPlan, "previousPlan");
        issues = immutable(issues, "issues");
        allowedPlaces = immutable(allowedPlaces, "allowedPlaces");
        allowedReferences = immutable(allowedReferences, "allowedReferences");
        if (issues.isEmpty()) throw new IllegalArgumentException("repair issues required");
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        values.forEach(value -> Objects.requireNonNull(value, field + " element"));
        return List.copyOf(values);
    }
}
