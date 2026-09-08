package com.wyx.travelagent.knowledge.retrieval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册任务十的城市知识检索配置属性和无状态引用校验器。
 * <p>
 * 本配置只负责对象装配，不查询数据库、不读取索引，也不访问来源 URL。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeRetrievalProperties.class)
public class KnowledgeRetrievalConfiguration {

    /**
     * 把无框架依赖的纯内存引用校验器暴露为 Bean，便于后续编排层复用。
     *
     * @return 无状态引用校验器
     */
    @Bean
    public CitationValidator citationValidator() {
        return new CitationValidator();
    }
}
