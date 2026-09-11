package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import java.time.Instant;

/**
 * 业务问题：在行程详情中提供轻量版本导航，避免重复嵌入全部版本内容。
 * 输入是用户隔离版本，输出主键、版本号和时间；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它服务任务十四详情契约；完整内容由版本接口提供，不负责 Trace、内部 JSON 或任务十五页面。
 * @param versionId 正版本主键
 * @param versionNumber 正版本号
 * @param createdAt 非空创建时间
 */
public record PlanVersionSummaryResponse(long versionId, int versionNumber, Instant createdAt) {
    /**
     * @param version 非空且已用户隔离版本
     * @return 非空轻量摘要
     * 映射只读内存，不访问外部资源或处理认证。
     */
    public static PlanVersionSummaryResponse from(TravelPlanRepository.PlanVersion version) {
        return new PlanVersionSummaryResponse(version.id(), version.versionNumber(), version.createdAt());
    }
}
