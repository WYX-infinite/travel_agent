package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.auth.JwtAuthenticationInterceptor;
import com.wyx.travelagent.auth.JwtProperties;
import com.wyx.travelagent.auth.JwtTokenService;
import com.wyx.travelagent.auth.Role;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.knowledge.ingest.TestDocumentFixtures;
import com.wyx.travelagent.knowledge.ingest.TikaDocumentParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class KnowledgeAdminControllerTest {

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;
    private JwtTokenService tokenService;
    private MockMultipartFile textFile;

    @BeforeEach
    void setUp() throws Exception {
        Path uploadRoot = temporaryDirectory.resolve("uploads");
        KnowledgeUploadProperties properties = new KnowledgeUploadProperties(
                uploadRoot, DataSize.ofKilobytes(64), 10_000);
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                properties, new TikaDocumentParser(10_000));
        KnowledgeAdminController controller = new KnowledgeAdminController(service);
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
                .andExpect(jsonPath("$.data.title").value("西湖游览说明"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.sha256").isString())
                .andExpect(jsonPath("$.error").doesNotExist());
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
