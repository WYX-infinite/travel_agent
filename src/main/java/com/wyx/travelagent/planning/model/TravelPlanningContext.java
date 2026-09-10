package com.wyx.travelagent.planning.model;

import java.util.Objects;

/**
 * 业务问题：提供一次规划所需的最小可信上下文，避免把聊天全文或用户秘密带入规划状态。
 * 输入是解析后需求，输出是不含聊天内容的不可变上下文；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责工具执行，任务十二负责生成后校验，后续编排消费本上下文；本类型不负责认证、聊天存储、生成、修正或追踪。
 *
 * @param resolvedRequirement 已验证需求与应用偏好的组合，不得为 {@code null}
 */
public record TravelPlanningContext(ResolvedTravelRequirement resolvedRequirement) {

    /**
     * 创建不携带聊天内容的规划上下文。
     * 输入不得为 null，显式空没有业务含义；输出仅保存解析后需求。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证；仅 null 前置条件失败时抛出参数异常。
     *
     * @throws NullPointerException 当解析后需求为 {@code null}
     */
    public TravelPlanningContext {
        Objects.requireNonNull(resolvedRequirement, "resolvedRequirement");
    }
}
