package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.ingest.DocumentParser;
import com.wyx.travelagent.knowledge.ingest.DocumentParsingException;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 负责管理员知识文档上传的元数据校验、受限流式写入、真实类型核验和 Tika 解析。
 * <p>
 * 输入为 multipart 文件、来源元数据和从 CurrentUserContext 得到的用户 ID，输出为安全落盘的
 * UPLOADED 文档与有效正文。Controller 不承载这些规则；本服务不访问来源 URL、不写数据库、
 * 不检查数据库哈希唯一键，也不实现任务九的 Embedding 与索引事务。
 */
@Service
public class KnowledgeDocumentUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentUploadService.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CITY_LENGTH = 100;
    private static final int MAX_PUBLISHER_LENGTH = 255;
    private static final int MAX_SOURCE_URL_LENGTH = 1024;
    private static final int MAX_FILENAME_LENGTH = 255;

    private final KnowledgeUploadProperties properties;
    private final DocumentParser parser;
    private final FileDeleter fileDeleter;

    /**
     * 创建上传服务。
     *
     * @param properties 上传根目录、文件字节上限和正文字符上限配置
     * @param parser     只解析已受控本地文件的 Tika 解析边界
     * @throws NullPointerException 任一依赖为空时抛出
     */
    @Autowired
    public KnowledgeDocumentUploadService(
            KnowledgeUploadProperties properties, DocumentParser parser) {
        this(properties, parser, Files::deleteIfExists);
    }

    KnowledgeDocumentUploadService(
            KnowledgeUploadProperties properties, DocumentParser parser, FileDeleter fileDeleter) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.fileDeleter = Objects.requireNonNull(fileDeleter, "fileDeleter");
    }

    /**
     * 校验并保存一份管理员上传的知识文档。
     * <p>
     * 文件扩展名和浏览器声明的 Content-Type 都可能被伪造，所以在流式写入后还会使用 Tika
     * 检测实际字节，三者必须明显一致。失败时清理本次临时文件和已移动的本次目标文件。
     *
     * @param file       multipart 文件，必须非空且实际字节数不超过配置上限
     * @param request    必填来源元数据
     * @param uploadedBy 已认证管理员的用户 ID，必须为正数
     * @return 已安全保存的不可变文档元数据和清理后正文
     * @throws KnowledgeUploadException 元数据、类型、大小、路径、解析或存储校验失败时抛出
     */
    public UploadedKnowledgeDocument upload(
            MultipartFile file, KnowledgeDocumentUploadRequest request, int uploadedBy) {
        ValidatedMetadata metadata = validateMetadata(request, uploadedBy);
        if (file == null || file.isEmpty()) {
            throw badRequest("EMPTY_UPLOAD", "uploaded file must not be empty");
        }

        String originalFilename = validateOriginalFilename(file.getOriginalFilename());
        SupportedFileFormat format = SupportedFileFormat.fromFilename(originalFilename);
        validateDeclaredContentType(format, file.getContentType());
        long maximumBytes = properties.maxFileSize().toBytes();
        if (file.getSize() > maximumBytes) {
            throw badRequest("UPLOAD_TOO_LARGE", "uploaded file exceeds the configured size limit");
        }

        Path temporaryFile = null;
        Path targetFile = null;
        boolean movedToTarget = false;
        boolean completed = false;
        Throwable primaryFailure = null;
        try {
            Path root = prepareUploadRoot();
            Path stagingRoot = prepareStagingRoot(root);
            temporaryFile = Files.createTempFile(stagingRoot, ".upload-", ".part");
            CopyResult copied = copyAndDigest(file, temporaryFile, maximumBytes);
            DocumentParser.ParsedDocument parsed = parseAndValidateType(temporaryFile, format);

            /*
             * 原始文件名只用于元数据显示。磁盘名称由服务端随机生成，并且扩展名来自白名单，
             * 从根本上避免用户控制 resolve 参数或覆盖同名文件。
             */
            String storedFilename = UUID.randomUUID() + format.storedExtension;
            targetFile = root.resolve(storedFilename).normalize();
            // normalize 后再次验证父目录，可在今后文件名生成策略改变时继续阻止路径穿越。
            if (!targetFile.getParent().equals(root) || !targetFile.startsWith(root)) {
                throw serverError("UPLOAD_STORAGE_FAILED", "upload target escaped the configured root", null);
            }
            try {
                Files.move(temporaryFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw serverError(
                        "UPLOAD_STORAGE_FAILED", "atomic upload finalization is unavailable", exception);
            }
            movedToTarget = true;

            Instant now = Instant.now();
            KnowledgeDocument document = new KnowledgeDocument(
                    null,
                    metadata.title,
                    metadata.city,
                    metadata.publisher,
                    metadata.sourceUrl,
                    request.publishedAt(),
                    request.documentType(),
                    originalFilename,
                    storedFilename,
                    parsed.detectedContentType(),
                    copied.bytes,
                    copied.sha256,
                    KnowledgeDocumentStatus.UPLOADED,
                    null,
                    uploadedBy,
                    now,
                    now);
            completed = true;
            return new UploadedKnowledgeDocument(document, parsed.text());
        } catch (KnowledgeUploadException exception) {
            primaryFailure = exception;
            throw exception;
        } catch (IOException exception) {
            KnowledgeUploadException converted = serverError(
                    "UPLOAD_STORAGE_FAILED", "uploaded file could not be stored", exception);
            primaryFailure = converted;
            throw converted;
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            IOException cleanupFailure = deleteForCleanup(temporaryFile);
            if (movedToTarget && !completed) {
                cleanupFailure = combineCleanupFailures(
                        cleanupFailure, deleteForCleanup(targetFile));
            }
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw serverError(
                            "UPLOAD_STORAGE_FAILED", "uploaded file cleanup failed", cleanupFailure);
                }
            }
        }
    }

    private ValidatedMetadata validateMetadata(KnowledgeDocumentUploadRequest request, int uploadedBy) {
        if (request == null || uploadedBy <= 0 || request.publishedAt() == null
                || request.documentType() == null) {
            throw badRequest("INVALID_UPLOAD_METADATA", "required upload metadata is missing");
        }
        String title = requiredText(request.title(), MAX_TITLE_LENGTH);
        String city = requiredText(request.city(), MAX_CITY_LENGTH);
        String publisher = requiredText(request.publisher(), MAX_PUBLISHER_LENGTH);
        if (title == null || city == null || publisher == null) {
            throw badRequest("INVALID_UPLOAD_METADATA", "required upload metadata is missing");
        }
        URI sourceUrl = validateSourceUrl(request.sourceUrl());
        return new ValidatedMetadata(title, city, publisher, sourceUrl);
    }

    private String requiredText(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip();
        return cleaned.isEmpty() || cleaned.length() > maximumLength ? null : cleaned;
    }

    private URI validateSourceUrl(String value) {
        String source = requiredText(value, MAX_SOURCE_URL_LENGTH);
        if (source == null) {
            throw badRequest("INVALID_SOURCE_URL", "source URL must be an absolute HTTP or HTTPS URL");
        }
        try {
            URI uri = new URI(source);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw badRequest(
                        "INVALID_SOURCE_URL", "source URL must be an absolute HTTP or HTTPS URL");
            }
            /*
             * 这里只构造 URI 并保存字符串元数据，绝不调用 URLConnection、HTTP 客户端或 DNS；
             * 因此用户填写的来源地址不能触发 SSRF、下载或可达性探测。
             */
            return uri;
        } catch (URISyntaxException exception) {
            throw badRequest("INVALID_SOURCE_URL", "source URL must be an absolute HTTP or HTTPS URL");
        }
    }

    private String validateOriginalFilename(String value) {
        if (value == null) {
            throw badRequest("INVALID_ORIGINAL_FILENAME", "original filename is required");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        boolean unsafeCharacter = normalized.chars()
                .anyMatch(character -> character == 0 || Character.isISOControl(character));
        if (normalized.isEmpty() || normalized.length() > MAX_FILENAME_LENGTH
                || normalized.contains("/") || normalized.contains("\\")
                || normalized.contains("..") || normalized.contains(":") || unsafeCharacter) {
            throw badRequest(
                    "INVALID_ORIGINAL_FILENAME", "original filename contains unsafe path content");
        }
        return normalized;
    }

    private void validateDeclaredContentType(SupportedFileFormat format, String contentType) {
        String normalized = normalizeContentType(contentType);
        if (!format.declaredContentTypes.contains(normalized)) {
            throw badRequest(
                    "DOCUMENT_TYPE_MISMATCH",
                    "filename extension and declared content type do not match");
        }
    }

    private Path prepareUploadRoot() throws IOException {
        Path configured = properties.uploadRoot().toAbsolutePath().normalize();
        Files.createDirectories(configured);
        return configured.toRealPath();
    }

    private Path prepareStagingRoot(Path root) throws IOException {
        Path staging = root.resolve(".staging").normalize();
        // 临时文件与正式文件隔离；真实路径复核可阻止预先放置的符号链接把 staging 引向根目录外。
        if (!staging.startsWith(root) || !staging.getParent().equals(root)) {
            throw new IOException("invalid staging directory");
        }
        Files.createDirectories(staging);
        Path realStaging = staging.toRealPath();
        if (!realStaging.startsWith(root) || !realStaging.getParent().equals(root)) {
            throw new IOException("invalid staging directory");
        }
        return realStaging;
    }

    private CopyResult copyAndDigest(MultipartFile file, Path destination, long maximumBytes)
            throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        /*
         * 不调用 getBytes()，而是按固定缓冲区流式读取。即便客户端伪造 multipart 大小，
         * 也会根据真实读取字节在越过上限的第一时间终止。
         */
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = Math.addExact(total, read);
                if (total > maximumBytes) {
                    throw badRequest(
                            "UPLOAD_TOO_LARGE", "uploaded file exceeds the configured size limit");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw badRequest("EMPTY_UPLOAD", "uploaded file must not be empty");
        }
        /*
         * SHA-256 由实际落盘字节计算，相同内容必然得到相同摘要。任务八只携带摘要；
         * 任务九才使用数据库唯一键判断重复文档。
         */
        return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw serverError("UPLOAD_STORAGE_FAILED", "SHA-256 is unavailable", exception);
        }
    }

    private DocumentParser.ParsedDocument parseAndValidateType(
            Path temporaryFile, SupportedFileFormat expectedFormat) {
        final DocumentParser.ParsedDocument parsed;
        try {
            parsed = parser.parse(temporaryFile);
        } catch (DocumentParsingException exception) {
            if (exception.detectedContentType() != null
                    && !expectedFormat.detectedContentTypes.contains(
                    normalizeContentType(exception.detectedContentType()))) {
                throw badRequest(
                        "DOCUMENT_TYPE_MISMATCH",
                        "file bytes do not match the approved document type");
            }
            throw new KnowledgeUploadException(
                    exception.code(), exception.getMessage(), HttpStatus.BAD_REQUEST, exception);
        }
        if (!expectedFormat.detectedContentTypes.contains(
                normalizeContentType(parsed.detectedContentType()))) {
            throw badRequest(
                    "DOCUMENT_TYPE_MISMATCH",
                    "file bytes do not match the approved document type");
        }
        return parsed;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String base = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        return base.strip().toLowerCase(Locale.ROOT);
    }

    private IOException deleteForCleanup(Path file) {
        if (file == null) {
            return null;
        }
        try {
            fileDeleter.deleteIfExists(file);
            return null;
        } catch (IOException exception) {
            /*
             * API 错误仍保持固定安全文案，不暴露本机路径；日志只记录服务端随机文件名。
             * 异常同时作为 suppressed cause 附到主错误，运维可发现清理失败而不会丢失原始解析原因。
             */
            LOGGER.warn("知识文档上传清理失败，文件名={}", file.getFileName(), exception);
            return exception;
        }
    }

    private IOException combineCleanupFailures(IOException first, IOException second) {
        if (first == null) {
            return second;
        }
        if (second != null) {
            first.addSuppressed(second);
        }
        return first;
    }

    private KnowledgeUploadException badRequest(String code, String message) {
        return new KnowledgeUploadException(code, message, HttpStatus.BAD_REQUEST);
    }

    private KnowledgeUploadException serverError(String code, String message, Throwable cause) {
        return new KnowledgeUploadException(
                code, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    private record CopyResult(long bytes, String sha256) {
    }

    private record ValidatedMetadata(String title, String city, String publisher, URI sourceUrl) {
    }

    private enum SupportedFileFormat {
        /** PDF 魔数、声明类型和 .pdf 扩展名必须一致。 */
        PDF(".pdf", Set.of("application/pdf"), Set.of("application/pdf")),
        /** DOCX 必须是 OOXML 文档，普通 ZIP 不能冒充。 */
        DOCX(
                ".docx",
                Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
        /** Markdown 本质是文本，因此真实检测允许 Tika 返回纯文本或 Markdown 类型。 */
        MARKDOWN(
                ".md",
                Set.of("text/markdown", "text/x-markdown", "text/plain"),
                Set.of("text/markdown", "text/x-markdown", "text/x-web-markdown", "text/plain")),
        /** TXT 只接受 Tika 确认的纯文本。 */
        TEXT(".txt", Set.of("text/plain"), Set.of("text/plain"));

        private static final Map<String, SupportedFileFormat> BY_EXTENSION = Map.of(
                ".pdf", PDF,
                ".docx", DOCX,
                ".md", MARKDOWN,
                ".markdown", MARKDOWN,
                ".txt", TEXT);

        private final String storedExtension;
        private final Set<String> declaredContentTypes;
        private final Set<String> detectedContentTypes;

        SupportedFileFormat(
                String storedExtension,
                Set<String> declaredContentTypes,
                Set<String> detectedContentTypes) {
            this.storedExtension = storedExtension;
            this.declaredContentTypes = declaredContentTypes;
            this.detectedContentTypes = detectedContentTypes;
        }

        private static SupportedFileFormat fromFilename(String filename) {
            String lower = filename.toLowerCase(Locale.ROOT);
            int separator = lower.lastIndexOf('.');
            if (separator <= 0) {
                throw new KnowledgeUploadException(
                        "UNSUPPORTED_DOCUMENT_TYPE",
                        "only PDF, DOCX, Markdown and TXT files are supported",
                        HttpStatus.BAD_REQUEST);
            }
            SupportedFileFormat format = BY_EXTENSION.get(lower.substring(separator));
            if (format == null) {
                throw new KnowledgeUploadException(
                        "UNSUPPORTED_DOCUMENT_TYPE",
                        "only PDF, DOCX, Markdown and TXT files are supported",
                        HttpStatus.BAD_REQUEST);
            }
            return format;
        }
    }
}

@FunctionalInterface
interface FileDeleter {
    void deleteIfExists(Path file) throws IOException;
}
