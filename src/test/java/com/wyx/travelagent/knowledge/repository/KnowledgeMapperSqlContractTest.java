package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalMetadata;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeMapperSqlContractTest {

    private SqlSessionFactory sessionFactory;

    @BeforeEach
    void setUp() {
        String databaseName = "knowledge_" + System.nanoTime();
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE knowledge_document (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    city VARCHAR(100) NOT NULL,
                    publisher VARCHAR(255) NOT NULL,
                    source_url VARCHAR(1024) NOT NULL,
                    published_at DATE NOT NULL,
                    document_type VARCHAR(32) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    stored_filename VARCHAR(255) NOT NULL,
                    content_type VARCHAR(100) NOT NULL,
                    file_size BIGINT NOT NULL,
                    sha256 CHAR(64) NOT NULL UNIQUE,
                    status VARCHAR(32) NOT NULL,
                    failure_reason CLOB,
                    uploaded_by INT NOT NULL,
                    created_at TIMESTAMP(6) NOT NULL,
                    updated_at TIMESTAMP(6) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE knowledge_chunk (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    chunk_index INT NOT NULL,
                    content CLOB NOT NULL,
                    embedding_id VARCHAR(128) NOT NULL UNIQUE,
                    created_at TIMESTAMP(6) NOT NULL,
                    UNIQUE (document_id, chunk_index)
                )
                """);

        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(KnowledgeDocumentMapper.class);
        configuration.addMapper(KnowledgeChunkMapper.class);
        configuration.addMapper(KnowledgeRetrievalMapper.class);
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void generatedKeysConditionalTransitionsAndActiveJoinMatchProductionContract() {
        Instant now = Instant.parse("2026-09-07T04:00:00Z");
        try (SqlSession session = sessionFactory.openSession(false)) {
            KnowledgeDocumentMapper documents = session.getMapper(KnowledgeDocumentMapper.class);
            KnowledgeChunkMapper chunks = session.getMapper(KnowledgeChunkMapper.class);
            KnowledgeDocumentRow row = KnowledgeDocumentRow.from(document('a', now));

            assertThat(documents.insert(row)).isEqualTo(1);
            assertThat(row.getId()).isPositive();
            assertThat(documents.findBySha256("a".repeat(64)).toDomain())
                    .extracting(KnowledgeDocument::status)
                    .isEqualTo(KnowledgeDocumentStatus.UPLOADED);
            assertThat(documents.transition(
                    row.getId(), "UPLOADED", "INDEXING", now.plusSeconds(1))).isEqualTo(1);
            assertThat(documents.transition(
                    row.getId(), "UPLOADED", "ACTIVE", now.plusSeconds(2))).isZero();

            KnowledgeChunkRow chunk = KnowledgeChunkRow.from(new KnowledgeChunk(
                    null, row.getId(), 0, "西湖游览正文", "knowledge:" + row.getId() + ":chunk:0", now));
            assertThat(chunks.insert(chunk)).isEqualTo(1);
            assertThat(chunk.getId()).isPositive();
            assertThat(chunks.findAllActive()).isEmpty();

            assertThat(documents.transition(
                    row.getId(), "INDEXING", "ACTIVE", now.plusSeconds(3))).isEqualTo(1);
            assertThat(chunks.findAllActive()).singleElement()
                    .extracting(KnowledgeChunkRow::toDomain)
                    .isEqualTo(new KnowledgeChunk(
                            chunk.getId(), row.getId(), 0, "西湖游览正文",
                            "knowledge:" + row.getId() + ":chunk:0", now));
            session.commit();
        }
    }

    @Test
    void databaseUniqueConstraintClosesConcurrentDuplicateWindow() {
        Instant now = Instant.parse("2026-09-07T04:00:00Z");
        try (SqlSession session = sessionFactory.openSession(false)) {
            KnowledgeDocumentMapper documents = session.getMapper(KnowledgeDocumentMapper.class);
            assertThat(documents.insert(KnowledgeDocumentRow.from(document('b', now)))).isEqualTo(1);

            assertThatThrownBy(() ->
                    documents.insert(KnowledgeDocumentRow.from(document('b', now.plusSeconds(1)))))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void retrievalMapperReturnsOneStableBatchForOnlyActiveTargetCity() {
        Instant now = Instant.parse("2026-09-07T04:00:00Z");
        try (SqlSession session = sessionFactory.openSession(false)) {
            KnowledgeDocumentMapper documents = session.getMapper(KnowledgeDocumentMapper.class);
            KnowledgeChunkMapper chunks = session.getMapper(KnowledgeChunkMapper.class);
            KnowledgeDocumentRow hangzhou = KnowledgeDocumentRow.from(document('c', now));
            hangzhou.setStatus("ACTIVE");
            KnowledgeDocumentRow inactive = KnowledgeDocumentRow.from(document('d', now));
            inactive.setStatus("FAILED");
            KnowledgeDocumentRow shanghai = KnowledgeDocumentRow.from(document('e', now));
            shanghai.setStatus("ACTIVE");
            shanghai.setCity("上海");
            documents.insert(hangzhou);
            documents.insert(inactive);
            documents.insert(shanghai);
            chunks.insert(KnowledgeChunkRow.from(new KnowledgeChunk(
                    null, hangzhou.getId(), 1, "杭州第二片", "knowledge:" + hangzhou.getId() + ":chunk:1", now)));
            chunks.insert(KnowledgeChunkRow.from(new KnowledgeChunk(
                    null, hangzhou.getId(), 0, "杭州第一片", "knowledge:" + hangzhou.getId() + ":chunk:0", now)));
            chunks.insert(KnowledgeChunkRow.from(new KnowledgeChunk(
                    null, inactive.getId(), 0, "失效片", "knowledge:" + inactive.getId() + ":chunk:0", now)));
            chunks.insert(KnowledgeChunkRow.from(new KnowledgeChunk(
                    null, shanghai.getId(), 0, "上海片", "knowledge:" + shanghai.getId() + ":chunk:0", now)));

            List<KnowledgeRetrievalRow> rows = session.getMapper(KnowledgeRetrievalMapper.class)
                    .findActiveByCity("杭州");

            assertThat(rows).hasSize(2)
                    .extracting(KnowledgeRetrievalRow::getChunkIndex).containsExactly(0, 1);
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.getDocumentId()).isEqualTo(hangzhou.getId());
                assertThat(row.getChunkId()).isPositive();
                assertThat(row.getCity()).isEqualTo("杭州");
            });
            List<KnowledgeRetrievalMetadata> domains = rows.stream()
                    .map(KnowledgeRetrievalRow::toDomain)
                    .toList();
            assertThat(domains).extracting(KnowledgeRetrievalMetadata::documentId)
                    .containsOnly(hangzhou.getId());
            assertThat(domains).extracting(KnowledgeRetrievalMetadata::chunkId)
                    .allSatisfy(id -> assertThat(id).isInstanceOf(Long.class).isPositive());
            assertThat(domains).extracting(KnowledgeRetrievalMetadata::chunkIndex)
                    .containsExactly(0, 1);
            assertThat(domains).extracting(KnowledgeRetrievalMetadata::content)
                    .containsExactly("杭州第一片", "杭州第二片");
            assertThat(domains).extracting(KnowledgeRetrievalMetadata::embeddingId)
                    .containsExactly("knowledge:" + hangzhou.getId() + ":chunk:0",
                            "knowledge:" + hangzhou.getId() + ":chunk:1");
            assertThat(domains).allSatisfy(metadata -> {
                assertThat(metadata.title()).isEqualTo("杭州知识文档");
                assertThat(metadata.city()).isEqualTo("杭州");
                assertThat(metadata.publisher()).isEqualTo("杭州市文旅局");
                assertThat(metadata.sourceUrl()).isEqualTo(URI.create("https://example.test/knowledge"));
                assertThat(metadata.publishedAt()).isEqualTo(LocalDate.of(2026, 9, 1));
                assertThat(metadata.documentType()).isEqualTo(KnowledgeDocumentType.OFFICIAL_NOTICE);
            });
            assertThat(session.getMapper(KnowledgeRetrievalMapper.class).findActiveByCity("不存在城市"))
                    .isEmpty();
        }
    }

    private KnowledgeDocument document(char digestCharacter, Instant now) {
        return new KnowledgeDocument(
                null,
                "杭州知识文档",
                "杭州",
                "杭州市文旅局",
                URI.create("https://example.test/knowledge"),
                LocalDate.of(2026, 9, 1),
                KnowledgeDocumentType.OFFICIAL_NOTICE,
                "guide.txt",
                "generated.txt",
                "text/plain",
                128,
                String.valueOf(digestCharacter).repeat(64),
                KnowledgeDocumentStatus.UPLOADED,
                null,
                9,
                now,
                now);
    }
}
