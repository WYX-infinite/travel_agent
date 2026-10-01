package com.wyx.travelagent.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {

    @Test
    void allowsPatchRequestsUsedByKnowledgeDocumentStatusUpdates() throws Exception {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        new com.wyx.travelagent.configuration.CorsConfiguration().addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/**");
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods()).contains("PATCH");

        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/admin/knowledge-documents/42/status");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8181);
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:8080");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(new DefaultCorsProcessor().processRequest(configuration, request, response))
                .isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PATCH");
    }

    private static final class ExposedCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return super.getCorsConfigurations();
        }
    }
}
