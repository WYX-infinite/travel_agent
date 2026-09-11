package com.wyx.travelagent.planning.persistence;

import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.validation.ValidationReport;

import java.util.Objects;

/**
 * 业务问题：持久化行程主记录和每个可解析版本，并原子推进当前版本与状态。
 * 实现可访问数据库，但不得调用模型、工具、缓存或处理认证；用户主体由上层显式传入。
 * 输入为非空领域对象，输出为稳定主键或写入副作用；接口本身不访问外部资源。任务十三只定义同步版本边界；本类型不负责事务实现、认证或异步任务状态机。
 */
public interface TravelPlanRepository {

    /**
     * 创建尚无版本的行程主记录。
     *
     * @param userId 认证层提供的正用户编号
     * @param requirement 非空不可变需求
     * @return 数据库生成的正行程编号
     */
    long create(int userId, TravelRequirement requirement);

    /**
     * 在一个事务内插入版本并更新主记录的 current_version/status。
     *
     * @param planId 正行程编号
     * @param draft 非空版本草稿
     * @return 版本表编号与版本号
     */
    SavedVersion saveVersion(long planId, VersionDraft draft);

    /**
     * 在没有新版本时更新主记录状态，例如模型调用失败。
     *
     * @param planId 正行程编号
     * @param status 非空最终状态
     */
    void updateStatus(long planId, TravelPlanStatus status);

    /**
     * @param versionNumber 从一开始且连续递增的版本号
     * @param plan 非空可解析行程
     * @param validationReport 非空全量 Java 校验报告
     * @param metrics 非空客观指标
     * @param status 保存后主记录应具有的状态
     */
    record VersionDraft(
            int versionNumber,
            ItineraryPlan plan,
            ValidationReport validationReport,
            PlanMetrics metrics,
            TravelPlanStatus status) {
        /** 创建完整版本草稿；只校验内存对象。 */
        public VersionDraft {
            if (versionNumber <= 0) throw new IllegalArgumentException("versionNumber");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(validationReport, "validationReport");
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * @param id 数据库版本主键
     * @param versionNumber 行程内版本号
     */
    record SavedVersion(long id, int versionNumber) {
        /** 创建已保存版本标识；两个编号必须为正。 */
        public SavedVersion {
            if (id <= 0 || versionNumber <= 0) throw new IllegalArgumentException("saved version");
        }
    }
}
