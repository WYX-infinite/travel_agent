package com.wyx.travelagent.knowledge.index;

import com.wyx.travelagent.knowledge.ingest.TextChunker;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配任务九的确定性切片器和持久化 LangChain4j 知识索引。
 * <p>
 * 输入是 Starter 已创建的生产 {@link EmbeddingModel} 和索引路径配置，输出是供上传入库服务使用的 Bean。
 * 本配置不自行创建或记录 API Key，不访问数据库或模型，也不提供任务十的城市过滤与引用校验。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeIndexProperties.class)
@MapperScan(
        basePackages = "com.wyx.travelagent.knowledge.repository",
        annotationClass = Mapper.class)
public class KnowledgeIndexConfiguration {

    /**
     * 提供任务八定义的无状态确定性切片器。
     *
     * @return 可复用的切片器
     */
    @Bean
    public TextChunker textChunker() {
        return new TextChunker();
    }

    /**
     * 使用 Starter 已配置的生产 EmbeddingModel 创建磁盘索引。
     *
     * @param embeddingModel LangChain4j 生产 Embedding 模型
     * @param properties 索引文件配置
     * @param modelName 写入索引格式并用于兼容性校验的模型标识
     * @return 支持两阶段入库的正式索引
     */
    @Bean
    public LangChain4jKnowledgeIndex knowledgeIndex(
            EmbeddingModel embeddingModel,
            KnowledgeIndexProperties properties,
            @Value("${TRAVEL_EMBEDDING_MODEL:${travel.ai.embedding-model:text-embedding-v3}}")
            String modelName) {
        return new LangChain4jKnowledgeIndex(embeddingModel, modelName, properties);
    }
}
