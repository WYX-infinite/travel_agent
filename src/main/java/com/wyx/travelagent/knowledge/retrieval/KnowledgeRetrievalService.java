package com.wyx.travelagent.knowledge.retrieval;

import com.wyx.travelagent.knowledge.index.KnowledgeIndex;
import com.wyx.travelagent.knowledge.index.KnowledgeMatch;
import com.wyx.travelagent.knowledge.index.KnowledgeQuery;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 编排数据库城市白名单、向量搜索、元数据一致性校验和引用阈值。
 * <p>
 * 数据库是城市与 ACTIVE 状态的事实来源；索引只负责在该白名单内排序，不能自行扩大范围。
 */
@Service
public final class KnowledgeRetrievalService {
    private static final Comparator<KnowledgeReference> STABLE_RANKING = Comparator
            .comparingDouble(KnowledgeReference::score).reversed()
            .thenComparing(KnowledgeReference::documentId)
            .thenComparingInt(KnowledgeReference::chunkIndex)
            .thenComparing(KnowledgeReference::chunkId);

    private final KnowledgeRetrievalMetadataSource metadataSource;
    private final KnowledgeIndex knowledgeIndex;
    private final KnowledgeRetrievalProperties properties;

    /**
     * @param metadataSource ACTIVE 城市元数据批量查询端口
     * @param knowledgeIndex 本地知识向量索引
     * @param properties 检索阈值和固定上限
     */
    public KnowledgeRetrievalService(
            KnowledgeRetrievalMetadataSource metadataSource,
            KnowledgeIndex knowledgeIndex,
            KnowledgeRetrievalProperties properties) {
        this.metadataSource = Objects.requireNonNull(metadataSource, "metadataSource");
        this.knowledgeIndex = Objects.requireNonNull(knowledgeIndex, "knowledgeIndex");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 在目标城市的 ACTIVE 切片内检索，返回候选与本次引用白名单。
     *
     * @param query 用户自然语言查询
     * @param city 目标城市精确名称
     * @return 不超过 8 个候选和不超过 5 个允许引用
     * @throws KnowledgeRetrievalException 输入非法或依赖返回不可信数据时抛出
     */
    public KnowledgeRetrievalResult retrieve(String query, String city) {
        String normalizedQuery = requireInput(query);
        String normalizedCity = requireInput(city);
        try {
            List<KnowledgeRetrievalMetadata> rows = List.copyOf(
                    Objects.requireNonNull(metadataSource.findActiveByCity(normalizedCity), "metadata rows"));
            if (rows.isEmpty()) {
                return new KnowledgeRetrievalResult(List.of(), List.of());
            }

            /*
             * 必须先由数据库建立城市/ACTIVE 白名单，再让索引在白名单内取 Top 8。
             * 若先做全局 Top 8，其他城市的高分条目会挤掉目标城市的有效结果。
             */
            Map<Long, KnowledgeRetrievalMetadata> byChunkId = uniqueMetadata(rows, normalizedCity);
            Set<Long> allowedChunkIds = Set.copyOf(byChunkId.keySet());
            List<KnowledgeMatch> matches = Objects.requireNonNull(
                    knowledgeIndex.search(new KnowledgeQuery(normalizedQuery),
                            allowedChunkIds, properties.candidateLimit()), "knowledge matches");

            List<KnowledgeReference> candidates = matches.stream()
                    .map(match -> trustedReference(match, byChunkId.get(match.chunkId())))
                    .filter(Objects::nonNull)
                    .sorted(STABLE_RANKING)
                    .limit(properties.candidateLimit())
                    .toList();
            List<KnowledgeReference> allowed = candidates.stream()
                    .filter(reference -> reference.score() >= properties.minScore())
                    .limit(properties.contextLimit())
                    .toList();
            return new KnowledgeRetrievalResult(candidates, allowed);
        } catch (KnowledgeRetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KnowledgeRetrievalException(
                    "KNOWLEDGE_RETRIEVAL_FAILED", "knowledge retrieval could not be completed", exception);
        }
    }

    private Map<Long, KnowledgeRetrievalMetadata> uniqueMetadata(
            List<KnowledgeRetrievalMetadata> rows, String city) {
        Map<Long, KnowledgeRetrievalMetadata> result = new HashMap<>();
        for (KnowledgeRetrievalMetadata row : rows) {
            if (row == null || !city.equals(row.city()) || result.putIfAbsent(row.chunkId(), row) != null) {
                throw new KnowledgeRetrievalException(
                        "KNOWLEDGE_RETRIEVAL_FAILED", "knowledge retrieval metadata is inconsistent");
            }
        }
        return Map.copyOf(result);
    }

    private KnowledgeReference trustedReference(
            KnowledgeMatch match, KnowledgeRetrievalMetadata metadata) {
        if (match == null || !Double.isFinite(match.score()) || match.score() < 0.0 || match.score() > 1.0) {
            throw new KnowledgeRetrievalException(
                    "KNOWLEDGE_RETRIEVAL_FAILED", "knowledge retrieval result is invalid");
        }
        if (metadata == null || !metadata.documentId().equals(match.documentId())
                || metadata.chunkIndex() != match.chunkIndex()
                || !metadata.embeddingId().equals(match.embeddingId())
                || !metadata.content().equals(match.content())) {
            return null;
        }
        return new KnowledgeReference(metadata.documentId(), metadata.chunkId(), metadata.chunkIndex(),
                metadata.title(), metadata.city(), metadata.publisher(), metadata.sourceUrl(),
                metadata.publishedAt(), metadata.documentType(), metadata.content(), match.score());
    }

    private String requireInput(String value) {
        if (value == null || value.isBlank()) {
            throw new KnowledgeRetrievalException(
                    "INVALID_KNOWLEDGE_QUERY", "knowledge query and city must not be blank");
        }
        return value.strip();
    }
}
