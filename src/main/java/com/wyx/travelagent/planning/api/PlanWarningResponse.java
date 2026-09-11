package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.validation.ValidationIssue;
import java.time.LocalDate;

/**
 * 业务问题：把 Java 校验结果投影为固定、安全的问题字段，避免序列化内部 ValidationIssue。
 * 输入是已经脱敏的问题，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四详情/版本接口；不负责重新校验、自动修正、Trace 或前端渲染。
 * @param code 非空稳定问题码
 * @param severity ERROR 或 WARNING
 * @param date 可为空问题日期
 * @param itemIndex 可为空零基活动序号
 * @param poiId 可为空 POI 标识
 * @param message 非空固定安全消息
 * @param knownValue 可为空已知事实摘要
 * @param requiredAdjustment 可为空所需调整摘要
 */
public record PlanWarningResponse(String code, String severity, LocalDate date, Integer itemIndex,
        String poiId, String message, String knownValue, String requiredAdjustment) {
    /**
     * @param issue 非空且已脱敏的校验问题
     * @return 非空稳定 DTO
     * 映射只读内存，不访问外部资源或处理认证；可选定位字段保持 null。
     */
    public static PlanWarningResponse from(ValidationIssue issue) {
        return new PlanWarningResponse(issue.code(), issue.severity().name(), issue.date(), issue.itemIndex(),
                issue.poiId(), issue.message(), issue.knownValue(), issue.requiredAdjustment());
    }
}
