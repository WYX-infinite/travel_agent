package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.planning.model.TravelPlanningContext;

/**
 * 业务问题：给编排器提供“单次修正”端口，使最多两次的上限只能由编排层控制。
 * 实现不得在内部重试或相信模型自报校验结果。
 * 输入为当前结构化请求与可信上下文，输出为单次执行快照；接口本身不访问外部资源，也不处理认证。任务十三控制调用上限；本类型不负责循环、Java 校验或持久化。
 */
@FunctionalInterface
public interface ItineraryRepairer {

    /**
     * 执行恰好一次结构化修正。
     *
     * @param request 非空当前轮结构化修正请求
     * @param originalContext 非空原始可信事实上下文
     * @return 一次模型调用的可解析结果及元数据
     * 实现可访问模型，但不得访问数据库、缓存或文件；认证由上层入口负责。
     */
    RepairExecution repair(RepairRequest request, TravelPlanningContext originalContext);
}
