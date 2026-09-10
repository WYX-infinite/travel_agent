package com.wyx.travelagent.planning.memory;

/**
 * 业务问题：标记最终偏好字段的真实来源，避免把本次请求与长期记忆混淆。
 * 输入由合并规则产生，输出是稳定来源枚举；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十只提供交通枚举，任务十二负责后续行程校验；后续编排仅消费来源，本类型不负责读取、写入、校验或行程生成。
 */
public enum PreferenceSource {
    /** 本次请求明确给值或明确清空。 */
    EXPLICIT_REQUEST,
    /** 本次请求缺失，采用已保存偏好。 */
    SAVED_PREFERENCE,
    /** 请求和长期偏好都没有提供值。 */
    NOT_PROVIDED
}
