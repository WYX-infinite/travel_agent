package com.wyx.travelagent.knowledge.retrieval;

import com.wyx.travelagent.knowledge.index.KnowledgeIndex;
import com.wyx.travelagent.knowledge.index.KnowledgeMatch;
import com.wyx.travelagent.knowledge.index.KnowledgeQuery;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeRetrievalServiceTest {

    @Test
    void retrievesOnlyActiveTargetCityBeforeTopEightAndBuildsAtMostFiveReferences() {
        List<KnowledgeRetrievalMetadata> metadata = java.util.stream.LongStream.rangeClosed(1, 9)
                .mapToObj(id -> metadata(id, 100 + id, "杭州", "杭州正文-" + id))
                .toList();
        CapturingMetadataSource source = new CapturingMetadataSource(metadata);
        List<KnowledgeMatch> ranking = new ArrayList<>();
        ranking.add(match(900, 9000, "上海高分", 0.99));
        ranking.add(match(901, 9001, "杭州失效高分", 0.98));
        for (int i = 1; i <= 9; i++) {
            ranking.add(match(i, 100 + i, "杭州正文-" + i, 0.90 - i * 0.01));
        }
        CapturingIndex index = new CapturingIndex(ranking);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                source, index, new KnowledgeRetrievalProperties(0.84, 8, 5));

        KnowledgeRetrievalResult result = service.retrieve("西湖怎么游览", " 杭州 ");

        assertThat(source.calls).isEqualTo(1);
        assertThat(source.lastCity).isEqualTo("杭州");
        assertThat(index.allowedChunkIds).containsExactlyInAnyOrderElementsOf(
                metadata.stream().map(KnowledgeRetrievalMetadata::chunkId).toList());
        assertThat(index.requestedLimit).isEqualTo(8);
        assertThat(result.candidateMatches()).hasSize(8)
                .extracting(KnowledgeReference::city).containsOnly("杭州");
        assertThat(result.allowedReferences()).hasSize(5)
                .extracting(KnowledgeReference::chunkId)
                .containsExactly(101L, 102L, 103L, 104L, 105L);
    }

    @Test
    void inclusiveThresholdStableTieOrderAndAllBelowThresholdAreHandled() {
        KnowledgeRetrievalMetadata first = metadata(2, 202, "杭州", "乙");
        KnowledgeRetrievalMetadata second = metadata(1, 101, "杭州", "甲");
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                city -> List.of(first, second),
                new CapturingIndex(List.of(match(2, 202, "乙", 0.70), match(1, 101, "甲", 0.70))),
                new KnowledgeRetrievalProperties(0.70, 8, 5));

        KnowledgeRetrievalResult result = service.retrieve("路线", "杭州");

        assertThat(result.allowedReferences()).extracting(KnowledgeReference::documentId)
                .containsExactly(1L, 2L);

        KnowledgeRetrievalService below = new KnowledgeRetrievalService(
                city -> List.of(second),
                new CapturingIndex(List.of(match(1, 101, "甲", 0.6999))),
                new KnowledgeRetrievalProperties(0.70, 8, 5));
        KnowledgeRetrievalResult belowResult = below.retrieve("路线", "杭州");
        assertThat(belowResult.candidateMatches()).hasSize(1);
        assertThat(belowResult.allowedReferences()).isEmpty();
    }

    @Test
    void noActiveCityRowsSkipsIndexAndMetadataMismatchFailsClosed() {
        CapturingIndex unused = new CapturingIndex(List.of(match(1, 101, "正文", 0.9)));
        KnowledgeRetrievalService empty = new KnowledgeRetrievalService(
                city -> List.of(), unused, new KnowledgeRetrievalProperties(0.0, 8, 5));
        assertThat(empty.retrieve("路线", "杭州").candidateMatches()).isEmpty();
        assertThat(unused.calls).isZero();

        KnowledgeRetrievalService mismatch = new KnowledgeRetrievalService(
                city -> List.of(metadata(1, 101, "杭州", "数据库正文")),
                new CapturingIndex(List.of(match(1, 101, "被篡改正文", 0.9))),
                new KnowledgeRetrievalProperties(0.0, 8, 5));
        assertThat(mismatch.retrieve("路线", "杭州").candidateMatches()).isEmpty();
    }

    @Test
    void invalidInputsScoresAndConfigFailWithStableSafeCodes() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                city -> List.of(metadata(1, 101, "杭州", "正文")),
                new CapturingIndex(List.of(match(1, 101, "正文", Double.NaN))),
                new KnowledgeRetrievalProperties(0.0, 8, 5));

        assertThatThrownBy(() -> service.retrieve(" ", "杭州"))
                .isInstanceOf(KnowledgeRetrievalException.class)
                .extracting("code").isEqualTo("INVALID_KNOWLEDGE_QUERY");
        assertThatThrownBy(() -> service.retrieve("路线", " "))
                .isInstanceOf(KnowledgeRetrievalException.class)
                .extracting("code").isEqualTo("INVALID_KNOWLEDGE_QUERY");
        assertThatThrownBy(() -> service.retrieve("路线", "杭州"))
                .isInstanceOf(KnowledgeRetrievalException.class)
                .extracting("code").isEqualTo("KNOWLEDGE_RETRIEVAL_FAILED");
        assertThatThrownBy(() -> new KnowledgeRetrievalProperties(1.01, 8, 5))
                .isInstanceOf(KnowledgeRetrievalException.class)
                .extracting("code").isEqualTo("INVALID_KNOWLEDGE_RETRIEVAL_CONFIG");
    }

    private static KnowledgeRetrievalMetadata metadata(long documentId, long chunkId, String city, String content) {
        return new KnowledgeRetrievalMetadata(documentId, chunkId, 0,
                "knowledge:" + documentId + ":chunk:0", content, "标题-" + documentId, city,
                "发布者", URI.create("https://example.test/source/" + documentId),
                LocalDate.of(2026, 9, 1), KnowledgeDocumentType.OFFICIAL_NOTICE);
    }

    private static KnowledgeMatch match(long documentId, long chunkId, String content, double score) {
        return new KnowledgeMatch("knowledge:" + documentId + ":chunk:0",
                documentId, chunkId, 0, content, score);
    }

    private static final class CapturingMetadataSource implements KnowledgeRetrievalMetadataSource {
        private final List<KnowledgeRetrievalMetadata> rows;
        private int calls;
        private String lastCity;

        private CapturingMetadataSource(List<KnowledgeRetrievalMetadata> rows) { this.rows = rows; }

        @Override
        public List<KnowledgeRetrievalMetadata> findActiveByCity(String city) {
            calls++;
            lastCity = city;
            return rows;
        }
    }

    private static final class CapturingIndex implements KnowledgeIndex {
        private final List<KnowledgeMatch> ranking;
        private int calls;
        private Set<Long> allowedChunkIds;
        private int requestedLimit;

        private CapturingIndex(List<KnowledgeMatch> ranking) { this.ranking = ranking; }

        @Override
        public List<KnowledgeMatch> search(KnowledgeQuery query, Set<Long> allowed, int maxResults) {
            calls++;
            allowedChunkIds = Set.copyOf(allowed);
            requestedLimit = maxResults;
            return ranking.stream().filter(match -> allowed.contains(match.chunkId())).limit(maxResults).toList();
        }

        @Override public List<KnowledgeMatch> search(KnowledgeQuery query, int maxResults) { return ranking; }
        @Override public void addAll(List<KnowledgeChunk> chunks) { throw new UnsupportedOperationException(); }
        @Override public void rebuild(List<KnowledgeChunk> chunks) { throw new UnsupportedOperationException(); }
    }
}
