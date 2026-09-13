package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.auth.JwtAuthenticationInterceptor;
import com.wyx.travelagent.auth.JwtProperties;
import com.wyx.travelagent.auth.JwtTokenService;
import com.wyx.travelagent.auth.Role;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.knowledge.ingest.KnowledgeIngestionService;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class KnowledgeAdministrationControllerContractTest {
    private static final Instant NOW = Instant.parse("2026-09-13T04:00:00Z");

    private MockMvc mockMvc;
    private JwtTokenService tokenService;
    private KnowledgeAdministrationService service;

    @BeforeEach
    void setUp() {
        service = mock(KnowledgeAdministrationService.class);
        KnowledgeDocumentSummaryResponse summary = new KnowledgeDocumentSummaryResponse(
                42L, "西湖游览说明", "杭州", "杭州市文化广电旅游局",
                URI.create("https://example.test/source"), LocalDate.of(2026, 8, 1),
                KnowledgeDocumentType.OFFICIAL_NOTICE, KnowledgeDocumentStatus.ACTIVE,
                2, NOW, NOW, null);
        KnowledgeDocumentDetailResponse detail = new KnowledgeDocumentDetailResponse(
                summary.id(), summary.title(), summary.city(), summary.publisher(),
                summary.sourceUrl(), summary.publishedAt(), summary.documentType(), summary.status(),
                summary.chunkCount(), summary.createdAt(), summary.updatedAt(), summary.failureReason(),
                "guide.txt", "text/plain", 128, "a".repeat(64));
        when(service.listDocuments()).thenReturn(List.of(summary));
        when(service.getDocument(42L)).thenReturn(detail);
        when(service.getChunks(42L, 0, 20)).thenReturn(new KnowledgeChunkPageResponse(
                List.of(new KnowledgeChunkPreviewResponse(88L, 42L, 0, "安全预览正文", NOW)),
                0, 20, 1, 1));
        when(service.changeStatus(42L, "DISABLED")).thenReturn(
                new KnowledgeDocumentDetailResponse(
                        detail.id(), detail.title(), detail.city(), detail.publisher(), detail.sourceUrl(),
                        detail.publishedAt(), detail.documentType(), KnowledgeDocumentStatus.DISABLED,
                        detail.chunkCount(), detail.createdAt(), NOW, detail.failureReason(),
                        detail.originalFilename(), detail.contentType(), detail.fileSize(), detail.sha256()));
        when(service.deleteDocument(42L)).thenReturn(
                new KnowledgeDocumentDeletionResponse(42L, true, NOW));
        when(service.reindex()).thenReturn(new KnowledgeReindexResponse(1, 2, NOW));

        tokenService = new JwtTokenService(new JwtProperties(
                System.getenv("TRAVEL_JWT_SECRET"), "travel-planning-agent", Duration.ofHours(2)));
        KnowledgeAdminController controller = new KnowledgeAdminController(
                mock(KnowledgeDocumentUploadService.class),
                mock(KnowledgeIngestionService.class),
                service);
        mockMvc = standaloneSetup(controller)
                .addInterceptors(new JwtAuthenticationInterceptor(tokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void adminCanListReadDetailAndPageChunksWithoutSensitiveFields() throws Exception {
        mockMvc.perform(admin(get("/api/admin/knowledge-documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(42))
                .andExpect(jsonPath("$.data[0].publishedAt").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].documentType").value("OFFICIAL_NOTICE"))
                .andExpect(jsonPath("$.data[0].chunkCount").value(2))
                .andExpect(jsonPath("$.data[0].storedFilename").doesNotExist())
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
        mockMvc.perform(admin(get("/api/admin/knowledge-documents/42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("guide.txt"))
                .andExpect(jsonPath("$.data.sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.storedFilename").doesNotExist());
        mockMvc.perform(admin(get("/api/admin/knowledge-documents/42/chunks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("安全预览正文"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].embeddingId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].vector").doesNotExist());
    }

    @Test
    void adminCanChangeStatusDeleteAndReindex() throws Exception {
        mockMvc.perform(admin(patch("/api/admin/knowledge-documents/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(admin(delete("/api/admin/knowledge-documents/42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(admin(post("/api/admin/knowledge-documents/reindex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeDocumentCount").value(1))
                .andExpect(jsonPath("$.data.chunkCount").value(2))
                .andExpect(jsonPath("$.data.rebuiltAt").isString());
    }

    @Test
    void invalidStatusMissingDocumentAndInvalidPageUseStableErrors() throws Exception {
        doThrow(new KnowledgeAdministrationException(
                "INVALID_KNOWLEDGE_DOCUMENT_STATUS", "status must be ACTIVE or DISABLED",
                HttpStatus.BAD_REQUEST)).when(service).changeStatus(42L, "ARCHIVED");
        doThrow(new KnowledgeAdministrationException(
                "KNOWLEDGE_DOCUMENT_NOT_FOUND", "knowledge document was not found",
                HttpStatus.NOT_FOUND)).when(service).getDocument(99L);
        doThrow(new KnowledgeAdministrationException(
                "INVALID_KNOWLEDGE_CHUNK_PAGE", "chunk page parameters are invalid",
                HttpStatus.BAD_REQUEST)).when(service).getChunks(42L, -1, 20);

        mockMvc.perform(admin(patch("/api/admin/knowledge-documents/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_KNOWLEDGE_DOCUMENT_STATUS"));
        mockMvc.perform(admin(get("/api/admin/knowledge-documents/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_DOCUMENT_NOT_FOUND"));
        mockMvc.perform(admin(get("/api/admin/knowledge-documents/42/chunks").param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_KNOWLEDGE_CHUNK_PAGE"));
        mockMvc.perform(admin(get("/api/admin/knowledge-documents/42/chunks").param("page", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void userIsForbiddenFromEveryManagementEndpointBeforeServiceRuns() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "user", Role.USER)).value();
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/admin/knowledge-documents"),
                get("/api/admin/knowledge-documents/42"),
                get("/api/admin/knowledge-documents/42/chunks"),
                patch("/api/admin/knowledge-documents/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"),
                delete("/api/admin/knowledge-documents/42"),
                post("/api/admin/knowledge-documents/reindex"));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + tokenService.issueToken(
                new CurrentUser(9, "admin", Role.ADMIN)).value());
    }
}
