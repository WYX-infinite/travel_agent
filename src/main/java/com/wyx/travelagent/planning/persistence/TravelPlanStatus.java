package com.wyx.travelagent.planning.persistence;

/**
 * 业务问题：表示行程主记录与版本保存时的同步规划状态。
 * 输入为当前版本校验/失败事实，输出为稳定数据库状态；本枚举不访问外部资源，也不处理认证。
 * 任务十三只使用同步规划状态；本类型不负责或提前实现任务十四的异步任务状态机。
 */
public enum TravelPlanStatus {
    /** 已保存中间版本，仍需继续修正。 */
    PLANNING,
    /** 当前版本没有 ERROR；WARNING 可保留。 */
    COMPLETED,
    /** 最多两次修正后仍有 ERROR。 */
    NEEDS_ADJUSTMENT,
    /** 后续步骤失败，但已有版本仍保留。 */
    FAILED
}
