package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.admin.KnowledgeAdministrationPersistence;
import com.wyx.travelagent.knowledge.model.KnowledgeChunk;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeAdministrationPersistenceTest {
    private JdbcTemplate jdbc;
    private KnowledgeDocumentMapper documents;
    private KnowledgeChunkMapper chunks;
    private MyBatisKnowledgeAdministrationPersistence persistence;

    @BeforeEach
    void setUp() {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:knowledge_admin_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
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
                    UNIQUE (document_id, chunk_index),
                    CONSTRAINT fk_admin_chunk_document
                        FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
                )
                """);

        Configuration configuration = new Configuration(new Environment(
                "test", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(KnowledgeDocumentMapper.class);
        configuration.addMapper(KnowledgeChunkMapper.class);
        configuration.addMapper(KnowledgeAdministrationMapper.class);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate sessions = new SqlSessionTemplate(sessionFactory);
        documents = sessions.getMapper(KnowledgeDocumentMapper.class);
        chunks = sessions.getMapper(KnowledgeChunkMapper.class);
        persistence = new MyBatisKnowledgeAdministrationPersistence(
                sessions.getMapper(KnowledgeAdministrationMapper.class),
                chunks,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Test
    void listUsesOneAggregateQueryIncludesZeroChunksAndHasStableNewestFirstOrder() {
        Instant old = Instant.parse("2026-09-12T04:00:00Z");
        Instant newer = Instant.parse("2026-09-13T04:00:00Z");
        KnowledgeDocumentRow zero = insert(document('a', old, KnowledgeDocumentStatus.UPLOADED));
        KnowledgeDocumentRow firstTie = insert(document('b', newer, KnowledgeDocumentStatus.ACTIVE));
        KnowledgeDocumentRow secondTie = insert(document('c', newer, KnowledgeDocumentStatus.DISABLED));
        insertChunk(firstTie.getId(), 0, "一");
        insertChunk(firstTie.getId(), 1, "二");

        List<KnowledgeAdministrationPersistence.DocumentRecord> result =
                persistence.findAllDocuments();

        assertThat(result).extracting(record -> record.document().id())
                .containsExactly(secondTie.getId(), firstTie.getId(), zero.getId());
        assertThat(result).extracting(KnowledgeAdministrationPersistence.DocumentRecord::chunkCount)
                .containsExactly(0L, 2L, 0L);
        assertThat(result.getFirst().document().status())
                .isEqualTo(KnowledgeDocumentStatus.DISABLED);
    }

    @Test
    void detailAndChunkPageAreAccurateStableAndBounded() {
        Instant now = Instant.parse("2026-09-13T04:00:00Z");
        KnowledgeDocumentRow row = insert(document('d', now, KnowledgeDocumentStatus.ACTIVE));
        KnowledgeChunkRow second = insertChunk(row.getId(), 1, "第二片");
        KnowledgeChunkRow first = insertChunk(row.getId(), 0, "第一片");
        KnowledgeChunkRow third = insertChunk(row.getId(), 2, "第三片");

        KnowledgeAdministrationPersistence.DocumentRecord detail =
                persistence.findDocument(row.getId()).orElseThrow();
        KnowledgeAdministrationPersistence.ChunkSlice firstPage =
                persistence.findChunks(row.getId(), 0, 2);
        KnowledgeAdministrationPersistence.ChunkSlice secondPage =
                persistence.findChunks(row.getId(), 1, 2);

        assertThat(detail.chunkCount()).isEqualTo(3);
        assertThat(firstPage.items()).extracting(KnowledgeChunk::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(secondPage.items()).extracting(KnowledgeChunk::id)
                .containsExactly(third.getId());
        assertThat(firstPage.totalElements()).isEqualTo(3);
    }

    @Test
    void conditionalStatusUpdateMapsDisabledWithoutOverwritingConcurrentState() {
        Instant now = Instant.parse("2026-09-13T04:00:00Z");
        KnowledgeDocumentRow row = insert(document('e', now, KnowledgeDocumentStatus.ACTIVE));

        assertThat(persistence.updateStatus(row.getId(), KnowledgeDocumentStatus.ACTIVE,
                KnowledgeDocumentStatus.DISABLED, now.plusSeconds(1))).isTrue();
        assertThat(persistence.updateStatus(row.getId(), KnowledgeDocumentStatus.ACTIVE,
                KnowledgeDocumentStatus.DISABLED, now.plusSeconds(2))).isFalse();
        assertThat(persistence.findDocument(row.getId()).orElseThrow().document().status())
                .isEqualTo(KnowledgeDocumentStatus.DISABLED);
    }

    @Test
    void conditionalDeleteCascadesChunksAndActiveSnapshotExcludesEveryNonActiveStatus() {
        Instant now = Instant.parse("2026-09-13T04:00:00Z");
        KnowledgeDocumentRow active = insert(document('f', now, KnowledgeDocumentStatus.ACTIVE));
        KnowledgeDocumentRow disabled = insert(document('1', now, KnowledgeDocumentStatus.DISABLED));
        KnowledgeChunkRow activeChunk = insertChunk(active.getId(), 0, "启用");
        insertChunk(disabled.getId(), 0, "停用");

        KnowledgeAdministrationPersistence.ActiveSnapshot snapshot =
                persistence.loadActiveSnapshot();
        assertThat(snapshot.activeDocumentCount()).isEqualTo(1);
        assertThat(snapshot.chunks()).extracting(KnowledgeChunk::id)
                .containsExactly(activeChunk.getId());
        assertThat(persistence.deleteDocument(disabled.getId(), KnowledgeDocumentStatus.FAILED))
                .isFalse();
        assertThat(persistence.deleteDocument(disabled.getId(), KnowledgeDocumentStatus.DISABLED))
                .isTrue();
        assertThat(persistence.findDocument(disabled.getId())).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = ?",
                Long.class, disabled.getId())).isZero();
    }

    private KnowledgeDocumentRow insert(KnowledgeDocument document) {
        KnowledgeDocumentRow row = KnowledgeDocumentRow.from(document);
        assertThat(documents.insert(row)).isEqualTo(1);
        return row;
    }

    private KnowledgeChunkRow insertChunk(Long documentId, int index, String content) {
        KnowledgeChunkRow row = KnowledgeChunkRow.from(new KnowledgeChunk(
                null, documentId, index, content,
                "knowledge:" + documentId + ":chunk:" + index,
                Instant.parse("2026-09-13T04:00:00Z")));
        assertThat(chunks.insert(row)).isEqualTo(1);
        return row;
    }

    private KnowledgeDocument document(
            char digest, Instant createdAt, KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                null, "杭州知识-" + digest, "杭州", "文旅局",
                URI.create("https://example.test/source/" + digest),
                LocalDate.of(2026, 9, 1), KnowledgeDocumentType.OFFICIAL_NOTICE,
                "guide.txt", "generated-" + digest + ".txt", "text/plain", 128,
                String.valueOf(digest).repeat(64), status,
                status == KnowledgeDocumentStatus.FAILED ? "safe failure" : null,
                9, createdAt, createdAt);
    }
}
