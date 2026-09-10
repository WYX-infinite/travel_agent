package com.wyx.travelagent.planning.memory;

/**
 * 业务问题：区分未请求、成功读取和读取失败，避免把数据库故障伪装成无记录。
 * 输入由服务读取路径产生，输出是稳定状态枚举；本类型不访问任何外部资源，也不处理认证。
 * 任务十与任务十二不产生本状态；后续编排和追踪可消费该状态，本类型不负责重试、告警或持久化。
 */
public enum PreferenceReadStatus {
    /** 调用方明确关闭长期偏好读取。 */
    NOT_REQUESTED,
    /** 仓储读取成功，包括成功但没有记录。 */
    SUCCEEDED,
    /** 仓储读取异常且本次规划已降级。 */
    FAILED
}
