package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalException;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalMetadata;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalMetadataSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * 使用 MyBatis 单次联表查询实现城市 ACTIVE 元数据只读端口。
 */
@Repository
public class MyBatisKnowledgeRetrievalMetadataSource implements KnowledgeRetrievalMetadataSource {
    private final KnowledgeRetrievalMapper mapper;

    /** @param mapper 城市知识联表 Mapper */
    public MyBatisKnowledgeRetrievalMetadataSource(KnowledgeRetrievalMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** {@inheritDoc} */
    @Override
    public List<KnowledgeRetrievalMetadata> findActiveByCity(String city) {
        try {
            return mapper.findActiveByCity(city).stream().map(KnowledgeRetrievalRow::toDomain).toList();
        } catch (RuntimeException exception) {
            throw new KnowledgeRetrievalException(
                    "KNOWLEDGE_RETRIEVAL_FAILED", "knowledge retrieval metadata could not be loaded", exception);
        }
    }
}
