package com.wyx.travelagent.knowledge.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/**
 * 绑定 {@code travel.knowledge} 下的知识文档上传安全配置。
 * <p>
 * {@code uploadRoot} 决定服务端文件根目录，默认 {@code data/uploads}；
 * {@code maxFileSize} 使用 Spring {@link DataSize} 表示字节大小，默认 10MB；
 * {@code maxExtractedCharacters} 默认 1,000,000 个 Java 字符，用于限制 Tika 输出。
 * 自动化测试必须显式传入 JUnit 临时目录，避免把测试文件写入真实 data 目录。
 *
 * @param uploadRoot             上传文件根目录，可由 {@code travel.knowledge.upload-root} 覆盖
 * @param maxFileSize            单个文件大小上限，可使用 KB、MB 等单位配置
 * @param maxExtractedCharacters 单个文档允许提取的最大 Java 字符数
 */
@ConfigurationProperties("travel.knowledge")
public record KnowledgeUploadProperties(
        Path uploadRoot,
        DataSize maxFileSize,
        int maxExtractedCharacters
) {
    private static final Path DEFAULT_UPLOAD_ROOT = Path.of("data", "uploads");
    private static final DataSize DEFAULT_MAX_FILE_SIZE = DataSize.ofMegabytes(10);
    private static final int DEFAULT_MAX_EXTRACTED_CHARACTERS = 1_000_000;

    /**
     * 应用安全默认值并校验配置边界。
     *
     * @throws IllegalArgumentException 文件上限或提取字符上限不是正数时抛出
     */
    public KnowledgeUploadProperties {
        if (uploadRoot == null) {
            uploadRoot = DEFAULT_UPLOAD_ROOT;
        }
        if (maxFileSize == null) {
            maxFileSize = DEFAULT_MAX_FILE_SIZE;
        }
        if (maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("travel.knowledge.max-file-size must be positive");
        }
        if (maxExtractedCharacters == 0) {
            maxExtractedCharacters = DEFAULT_MAX_EXTRACTED_CHARACTERS;
        }
        if (maxExtractedCharacters < 0) {
            throw new IllegalArgumentException(
                    "travel.knowledge.max-extracted-characters must be positive");
        }
    }
}
