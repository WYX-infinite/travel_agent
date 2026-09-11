package com.wyx.travelagent.planning.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册任务十三新增的行程版本与 Trace MyBatis 映射。
 * 输入是应用现有 MyBatis 会话工厂，输出为包内 mapper 代理；本配置不读取业务数据、模型响应或认证信息。
 * 任务十三仅增加 mapper 扫描，不修改数据库结构；本类型不负责建表、事务业务规则或异步任务。
 */
@Configuration
@MapperScan(
        basePackages = {
                "com.wyx.travelagent.planning.persistence",
                "com.wyx.travelagent.planning.task",
                "com.wyx.travelagent.trace"
        },
        annotationClass = Mapper.class)
class PlanningPersistenceConfiguration {
}
