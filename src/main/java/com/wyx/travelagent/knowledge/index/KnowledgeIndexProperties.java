package com.wyx.travelagent.knowledge.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 绑定本地知识索引正式文件路径，并派生固定名称的同目录临时文件。
 * <p>
 * 生产默认写入 {@code data/index/knowledge-index.json}；测试必须显式传入 JUnit 临时目录。
 * 本配置不读取 Embedding 密钥，也不负责数据库、上传目录或任务十检索策略。
 *
 * @param indexFile 正式 JSON 索引文件；为空时使用项目默认路径
 * @param embeddingBatchSize 单次提交给 Embedding 服务的最大文本切片数
 */
@ConfigurationProperties("travel.knowledge")
public record KnowledgeIndexProperties(
        Path indexFile,
        @org.springframework.boot.context.properties.bind.DefaultValue("10")
        int embeddingBatchSize) {
    private static final Path DEFAULT_INDEX_FILE =
            Path.of("data", "index", "knowledge-index.json");
    private static final int DEFAULT_EMBEDDING_BATCH_SIZE = 10;

    public KnowledgeIndexProperties(Path indexFile) {
        this(indexFile, DEFAULT_EMBEDDING_BATCH_SIZE);
    }

    /**
     * 应用安全默认值并规范化路径。
     */
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public KnowledgeIndexProperties {
        if (indexFile == null) {
            indexFile = DEFAULT_INDEX_FILE;
        }
        indexFile = indexFile.normalize();
        if (embeddingBatchSize < 1) {
            throw new IllegalArgumentException(
                    "travel.knowledge.embedding-batch-size must be greater than zero");
        }
    }

    /**
     * 返回正式索引同目录下固定的临时文件路径。
     *
     * @return 名称恒为 {@code knowledge-index.json.tmp} 的路径
     */
    public Path temporaryFile() {
        Path parent = indexFile.getParent();
        Path filename = Path.of("knowledge-index.json.tmp");
        return parent == null ? filename : parent.resolve(filename);
    }
}
