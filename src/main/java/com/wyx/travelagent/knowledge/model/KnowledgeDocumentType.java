package com.wyx.travelagent.knowledge.model;

/**
 * 表示管理员为知识文档选择的业务类别。
 * <p>
 * 该枚举接收上传表单中的类别名称，并作为 {@link KnowledgeDocument} 的元数据输出；
 * 它不判断文件的 PDF、DOCX 等物理格式，也不负责任务九的检索或向量索引。
 */
public enum KnowledgeDocumentType {
    /** 政府部门或其他权威机构正式发布的公告。 */
    OFFICIAL_NOTICE,
    /** 景区或场馆发布的游览规则、开放说明等资料。 */
    ATTRACTION_GUIDE,
    /** 交通主管部门或运营机构发布的出行指南。 */
    TRANSPORT_GUIDE,
    /** 管理员依据明确来源自行整理的资料。 */
    MANUAL_CURATION
}
