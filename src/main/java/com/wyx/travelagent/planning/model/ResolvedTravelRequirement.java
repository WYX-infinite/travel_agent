package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import java.util.Objects;

/**
 * 业务问题：将已验证旅行需求与实际采用的偏好快照绑定，避免后续编排丢失偏好来源。
 * 输入是强类型需求与应用快照，输出是单一不可变解析结果；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责工具边界，任务十二负责行程校验，后续编排消费本结果；本类型不负责偏好读取、认证、行程生成或修正。
 *
 * @param requirement 完成合并与校验的强类型旅行需求
 * @param appliedPreferenceSnapshot 本次实际应用的已保存偏好快照
 */
public record ResolvedTravelRequirement(
        TravelRequirement requirement,
        AppliedPreferenceSnapshot appliedPreferenceSnapshot) {

    /**
     * 创建解析结果。
     * 两个输入均不得为 null，输出保持原不可变对象引用；不接受显式空替代必要对象。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证；仅前置条件失败时抛出参数异常。
     *
     * @throws NullPointerException 当需求或快照为 {@code null}
     */
    public ResolvedTravelRequirement {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(appliedPreferenceSnapshot, "appliedPreferenceSnapshot");
    }
}
