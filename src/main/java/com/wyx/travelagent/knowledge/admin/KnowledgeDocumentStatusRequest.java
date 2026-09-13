package com.wyx.travelagent.knowledge.admin;

/** 启停请求保留原始字符串，由服务返回专用未知状态错误码。 */
public record KnowledgeDocumentStatusRequest(String status) {
}
