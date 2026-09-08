package com.wyx.travelagent.knowledge.retrieval;

import java.util.List;
import java.util.Objects;

/**
 * 引用白名单校验结果。
 * @param validReferences 按本次检索顺序输出的有效引用
 * @param rejectedChunkIds 去重且按首次提交顺序保留的伪造正数 ID
 * @param invalidValueCount null、零或负数值的出现次数
 * @param issues 稳定安全问题；存在任何无效提交时只含 INVALID_CITATION
 */
public record CitationValidationResult(
        List<KnowledgeReference> validReferences,
        List<Long> rejectedChunkIds,
        int invalidValueCount,
        List<CitationIssue> issues) {
    /** 创建防御性不可变校验结果。 */
    public CitationValidationResult {
        validReferences = List.copyOf(Objects.requireNonNull(validReferences, "validReferences"));
        rejectedChunkIds = List.copyOf(Objects.requireNonNull(rejectedChunkIds, "rejectedChunkIds"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (invalidValueCount < 0) throw new IllegalArgumentException("invalidValueCount must not be negative");
    }
}
