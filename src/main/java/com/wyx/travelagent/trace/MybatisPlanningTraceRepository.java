package com.wyx.travelagent.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * 业务问题：把字段白名单 Trace 详情编码为 JSON 并写入既有 planning_trace 表。
 * 输入不含敏感原文，输出仅为插入副作用；本适配器不调用模型、工具、缓存或处理认证。
 * 本类型只访问数据库，认证由上层入口负责。任务十三复用既有表结构；本类型不负责脱敏决策、Trace 查询或异步任务。
 */
@Repository
public class MybatisPlanningTraceRepository implements PlanningTraceRepository {
    private final PlanningTraceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 MyBatis Trace 写入适配器。
     *
     * @param mapper 非空 Trace 写映射
     * @param objectMapper 非空应用 JSON 编码器
     * 构造只复制内存依赖，不访问数据库、模型、缓存、文件或网络，也不处理认证。
     */
    public MybatisPlanningTraceRepository(PlanningTraceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy().findAndRegisterModules();
    }

    /**
     * 写入一条脱敏事件；失败使用稳定代码且不保留 SQL 或底层异常。
     *
     * @param event 非空且已校验的脱敏事件
     * 本方法只写入数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Override
    public void append(PlanningTraceEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            String details = objectMapper.writeValueAsString(event.details());
            PlanningTraceRow row = new PlanningTraceRow(event, details);
            if (mapper.insert(row) != 1) throw failure();
        } catch (JsonProcessingException | RuntimeException exception) {
            throw failure();
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("PLANNING_TRACE_PERSISTENCE_FAILED");
    }
}
