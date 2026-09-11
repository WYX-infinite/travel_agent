package com.wyx.travelagent.planning.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.model.TravelRequirement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * 业务问题：在既有 travel_plan/travel_plan_version 表中保存可解析版本，并保证插入版本与推进主记录原子提交。
 * 输入只接受内部领域对象，输出数据库主键；本适配器不调用模型、工具、缓存或处理认证。
 * 本类型只访问数据库，认证由上层入口负责。任务十三复用既有表结构；本类型不负责建表、模型生成或异步任务调度。
 */
@Repository
public class MybatisTravelPlanRepository implements TravelPlanRepository {
    private final TravelPlanMapper planMapper;
    private final TravelPlanVersionMapper versionMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建 MyBatis 版本仓储。
     *
     * @param planMapper 非空主记录写映射
     * @param versionMapper 非空版本写映射
     * @param transactionTemplate 非空 Spring 事务模板
     * @param objectMapper 非空应用 JSON 编码器
     * 构造只复制内存依赖，不访问数据库、模型、缓存、文件或网络，也不处理认证。
     */
    public MybatisTravelPlanRepository(
            TravelPlanMapper planMapper,
            TravelPlanVersionMapper versionMapper,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper) {
        this.planMapper = Objects.requireNonNull(planMapper, "planMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy().findAndRegisterModules();
    }

    /**
     * 创建 current_version=0 的主记录；失败使用稳定异常，不泄露 SQL。
     *
     * @param userId 认证层显式提供的正用户编号
     * @param requirement 非空不可变需求
     * @return 数据库生成的正行程编号
     * 本方法只写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override
    public long create(int userId, TravelRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        TravelPlanRow row = new TravelPlanRow(userId, requirement.destinationCity(),
                requirement.startDate(), requirement.endDate(), TravelPlanStatus.PLANNING.name());
        try {
            if (planMapper.insert(row) != 1 || row.getId() == null || row.getId() <= 0) throw failure();
            return row.getId();
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    /**
     * 在同一事务中插入 JSON 版本并推进主记录；任一步失败都会回滚。
     *
     * @param planId 正行程编号
     * @param draft 非空版本草稿
     * @return 已保存版本主键和版本号
     * 本方法只写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override
    public SavedVersion saveVersion(long planId, VersionDraft draft) {
        Objects.requireNonNull(draft, "draft");
        try {
            SavedVersion saved = transactionTemplate.execute(status -> {
                TravelPlanVersionRow row = new TravelPlanVersionRow(planId, draft.versionNumber(),
                        json(draft.plan()), json(draft.validationReport()), json(draft.metrics()));
                if (versionMapper.insert(row) != 1 || row.getId() == null || row.getId() <= 0) throw failure();
                if (planMapper.advanceVersion(planId, draft.versionNumber(), draft.status().name()) != 1) {
                    throw failure();
                }
                return new SavedVersion(row.getId(), draft.versionNumber());
            });
            return Objects.requireNonNull(saved, "transaction result");
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    /**
     * 更新已有主记录状态；失败使用稳定异常。
     *
     * @param planId 正行程编号
     * @param status 非空目标状态
     * 本方法只写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override
    public void updateStatus(long planId, TravelPlanStatus status) {
        Objects.requireNonNull(status, "status");
        try {
            if (planMapper.updateStatus(planId, status.name()) != 1) throw failure();
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure();
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("PLAN_PERSISTENCE_FAILED");
    }
}
