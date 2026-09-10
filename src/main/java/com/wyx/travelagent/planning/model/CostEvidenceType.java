package com.wyx.travelagent.planning.model;

/**
 * 业务问题：标记每笔已知费用的真实证据边界，避免把模型估算展示成实时事实。
 * 输入由可信事实或模型结构化响应给出，输出为稳定证据枚举；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供 FACT 工具事实，任务十知识切片支持 DOCUMENT，任务十二校验来源；后续编排展示来源，本类型不负责查价、引用检索或修正。
 */
public enum CostEvidenceType {
    /** 费用来自本次工具事实。 */
    FACT,
    /** 费用来自本次允许的知识切片。 */
    DOCUMENT,
    /** 费用仅为模型估算，必须明确展示为估算。 */
    AI_ESTIMATE
}
