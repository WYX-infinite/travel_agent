package com.wyx.travelagent.planning;

import com.wyx.travelagent.trace.PlanningStep;

/**
 * 业务问题：把任务十三同步编排的真实阶段变化通知给任务十四持久化状态机。
 * 输入是实际开始执行的步骤，输出为空；实现可以写任务数据库，但本接口自身不访问模型、数据库、缓存、文件、线程池或网络，也不处理认证。
 * 它只连接任务十三与任务十四；不负责写 Trace、发送 SSE、改变编排顺序或伪造尚未发生的阶段。
 */
@FunctionalInterface
public interface PlanningProgressListener {
    /**
     * 通知一个真实阶段已经开始。
     * @param step 非空规划步骤；调用方保证按编排顺序通知
     * 实现可以写任务数据库，但不得调用模型或工具；用户身份由异步服务显式传递，本方法不读取认证上下文。
     */
    void onStep(PlanningStep step);

    /**
     * 返回不执行副作用的监听器。
     * @return 永不为空的空监听器
     * 无输入与缺失语义，不访问任何外部资源，也不处理认证。
     */
    static PlanningProgressListener none() {
        return step -> { };
    }
}
