package com.wyx.travelagent.planning.validation;

import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.TravelPlanningContext;

import java.util.List;

/**
 * 业务问题：把各项确定性行程规则组合为统一接口，避免校验器依赖模型判断。
 * 输入为可信规划上下文和模型草稿，输出为该规则发现的问题；本类型不访问模型、数据库、缓存、文件或网络，也不处理认证。
 * 任务十提供内部事实，任务十二实现规则，后续任务十三只重新运行全部规则；本接口不负责生成 Prompt、自动修正或持久化。
 */
@FunctionalInterface
public interface ItineraryRule {
    /**
     * 仅使用给定内存对象校验一条规则。
     *
     * @param context 非空可信规划上下文，事实缺失必须用 WARNING 表达而不能猜测
     * @param plan 非空模型草稿，仍视为不可信并必须完整校验
     * @return 非空、可为空集合的问题列表
     * @throws NullPointerException 当上下文或计划为空
     * 实现不得调用模型或访问数据库、缓存、文件、网络和认证上下文。
     */
    List<ValidationIssue> validate(TravelPlanningContext context, ItineraryPlan plan);
}
