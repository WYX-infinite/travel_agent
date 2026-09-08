package com.wyx.travelagent.knowledge.retrieval;

/**
 * 表示引用校验产生的安全、稳定问题。
 *
 * @param code 稳定问题码
 * @param message 不含用户提交 ID 或数据库细节的安全消息
 */
public record CitationIssue(String code, String message) {
    /** 校验问题码与消息均非空。 */
    public CitationIssue {
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("citation issue is invalid");
        }
    }
}
