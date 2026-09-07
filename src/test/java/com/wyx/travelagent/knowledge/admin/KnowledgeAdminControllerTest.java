package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.auth.JwtAuthenticationInterceptor;
import com.wyx.travelagent.auth.JwtProperties;
import com.wyx.travelagent.auth.JwtTokenService;
import com.wyx.travelagent.auth.Role;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.knowledge.ingest.KnowledgeIngestionService;
import com.wyx.travelagent.knowledge.ingest.KnowledgeIngestionException;
import com.wyx.travelagent.knowledge.ingest.TestDocumentFixtures;
import com.wyx.travelagent.knowledge.ingest.TikaDocumentParser;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class KnowledgeAdminControllerTest {

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;
    private JwtTokenService tokenService;
    private KnowledgeIngestionService ingestionService;
    private MockMultipartFile textFile;

    @BeforeEach
    void setUp() throws Exception {
        Path uploadRoot = temporaryDirectory.resolve("uploads");
        KnowledgeUploadProperties properties = new KnowledgeUploadProperties(
                uploadRoot, DataSize.ofKilobytes(64), 10_000);
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                properties, new TikaDocumentParser(10_000));
        ingestionService = mock(KnowledgeIngestionService.class);
        when(ingestionService.ingest(any())).thenAnswer(invocation -> {
            KnowledgeDocument source = invocation
                    .<com.wyx.travelagent.knowledge.admin.UploadedKnowledgeDocument>getArgument(0)
                    .document();
            return new KnowledgeDocument(
                    42L, source.title(), source.city(), source.publisher(), source.sourceUrl(),
                    source.publishedAt(), source.documentType(), source.originalFilename(),
                    source.storedFilename(), source.contentType(), source.fileSize(), source.sha256(),
                    KnowledgeDocumentStatus.ACTIVE, null, source.uploadedBy(),
                    source.createdAt(), source.updatedAt());
        });
        KnowledgeAdminController controller = new KnowledgeAdminController(service, ingestionService);
        tokenService = new JwtTokenService(
                new JwtProperties(System.getenv("TRAVEL_JWT_SECRET"),
                        "travel-planning-agent", Duration.ofHours(2)));
        JwtAuthenticationInterceptor interceptor = new JwtAuthenticationInterceptor(tokenService);
        mockMvc = standaloneSetup(controller)
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        Path fixture = TestDocumentFixtures.text(temporaryDirectory, "guide.txt");
        textFile = new MockMultipartFile(
                "file", "guide.txt", "text/plain", Files.readAllBytes(fixture));
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void adminCanUploadDocumentThroughUnifiedApiResponse() throws Exception {
        mockMvc.perform(validUpload(adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.title").value("西湖游览说明"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sha256").isString())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void ingestionFailureUsesStableUnifiedErrorWithoutLeakingCause() throws Exception {
        doThrow(new KnowledgeIngestionException(
                "KNOWLEDGE_INDEX_PERSIST_FAILED",
                "knowledge index could not be atomically persisted",
                HttpStatus.INTERNAL_SERVER_ERROR,
                new IllegalStateException("C:\\private\\index API_KEY 正文向量")))
                .when(ingestionService).ingest(any());

        mockMvc.perform(validUpload(adminToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_INDEX_PERSIST_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("knowledge index could not be atomically persisted"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void ordinaryUserReceives403BeforeUploadLogicRuns() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "user", Role.USER)).value();

        mockMvc.perform(validUpload(token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void missingMultipartMetadataUsesUnifiedValidationResponse() throws Exception {
        mockMvc.perform(multipart("/api/admin/knowledge-documents")
                        .file(textFile)
                        .param("title", "标题")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidSourceUrlUsesSafeBusinessError() throws Exception {
        mockMvc.perform(multipart("/api/admin/knowledge-documents")
                        .file(textFile)
                        .param("title", "标题")
                        .param("city", "杭州")
                        .param("publisher", "文旅局")
                        .param("sourceUrl", "file:///etc/passwd")
                        .param("publishedAt", "2026-08-01")
                        .param("documentType", "OFFICIAL_NOTICE")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SOURCE_URL"));
    }

    @Test
    void unsupportedBusinessDocumentTypeUsesSafeBusinessError() throws Exception {
        mockMvc.perform(multipart("/api/admin/knowledge-documents")
                        .file(textFile)
                        .param("title", "标题")
                        .param("city", "杭州")
                        .param("publisher", "文旅局")
                        .param("sourceUrl", "https://example.test/source")
                        .param("publishedAt", "2026-08-01")
                        .param("documentType", "TRAVEL_BLOG")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_DOCUMENT_TYPE"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validUpload(
            String token) {
        return multipart("/api/admin/knowledge-documents")
                .file(textFile)
                .param("title", "西湖游览说明")
                .param("city", "杭州")
                .param("publisher", "杭州市文化广电旅游局")
                .param("sourceUrl", "https://example.test/source")
                .param("publishedAt", "2026-08-01")
                .param("documentType", "OFFICIAL_NOTICE")
                .header("Authorization", "Bearer " + token);
    }

    private String adminToken() {
        return tokenService.issueToken(new CurrentUser(9, "admin", Role.ADMIN)).value();
    }
}
