package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.common.api.ApiResponse;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * 暴露管理员知识文档上传入口，并沿用现有 {@code /api/admin/**} JWT 角色鉴权。
 * <p>
 * Controller 接收 multipart 字段，从 CurrentUserContext 读取上传用户并返回统一 ApiResponse；
 * 元数据校验、类型检测、解析和文件存储均委托服务，本类不访问来源 URL、不写数据库，也不实现任务九索引。
 */
@RestController
@RequestMapping("/api/admin/knowledge-documents")
public class KnowledgeAdminController {
    private final KnowledgeDocumentUploadService uploadService;

    /**
     * 创建保持轻量的管理员上传 Controller。
     *
     * @param uploadService 承担全部上传校验、解析和存储规则的服务
     */
    public KnowledgeAdminController(KnowledgeDocumentUploadService uploadService) {
        this.uploadService = Objects.requireNonNull(uploadService, "uploadService");
    }

    /**
     * 上传并验证一份带来源元数据的知识文档。
     *
     * @param file         PDF、DOCX、Markdown 或 TXT 文件
     * @param title        文档标题
     * @param city         适用城市
     * @param publisher    发布机构
     * @param sourceUrl    仅保存、不访问的 HTTP/HTTPS 来源 URL
     * @param publishedAt  ISO-8601 格式发布日期
     * @param documentType 文档业务类别
     * @return HTTP 201 和不含正文、绝对路径的统一上传摘要
     * @throws KnowledgeUploadException 当前用户缺失、日期/类别非法或服务校验失败时抛出
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeDocumentUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("city") String city,
            @RequestParam("publisher") String publisher,
            @RequestParam("sourceUrl") String sourceUrl,
            @RequestParam("publishedAt") String publishedAt,
            @RequestParam("documentType") String documentType) {
        CurrentUser currentUser = CurrentUserContext.get().orElseThrow(() ->
                new KnowledgeUploadException(
                        "UNAUTHORIZED", "authentication required", HttpStatus.UNAUTHORIZED));
        KnowledgeDocumentUploadRequest request = new KnowledgeDocumentUploadRequest(
                title,
                city,
                publisher,
                sourceUrl,
                parsePublishedAt(publishedAt),
                parseDocumentType(documentType));
        KnowledgeDocument document = uploadService.upload(file, request, currentUser.userId()).document();
        KnowledgeDocumentUploadResponse response = new KnowledgeDocumentUploadResponse(
                document.title(),
                document.status(),
                document.originalFilename(),
                document.storedFilename(),
                document.contentType(),
                document.fileSize(),
                document.sha256());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    private LocalDate parsePublishedAt(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException | NullPointerException exception) {
            throw new KnowledgeUploadException(
                    "INVALID_UPLOAD_METADATA",
                    "publishedAt must be an ISO-8601 date",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private KnowledgeDocumentType parseDocumentType(String value) {
        try {
            return KnowledgeDocumentType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new KnowledgeUploadException(
                    "UNSUPPORTED_DOCUMENT_TYPE",
                    "documentType is unsupported",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
