package com.wyx.travelagent.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-09-04T06:00:00Z");
    private static final String SECRET = encodedSecret("0123456789abcdef0123456789abcdef");
    private static final String OTHER_SECRET = encodedSecret("abcdef0123456789abcdef0123456789");

    private JwtAuthenticationInterceptor interceptor;
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(
                new JwtProperties(SECRET, "travel-planning-agent", Duration.ofHours(2)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        interceptor = new JwtAuthenticationInterceptor(tokenService);
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void missingBearerTokenReturns401() throws Exception {
        MockHttpServletResponse response = authorize("/api/plans", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void tokenWithWrongSignatureReturns401() throws Exception {
        String token = JwtTokenServiceTest.token(
                OTHER_SECRET, "travel-planning-agent", NOW, NOW.plusSeconds(3600));

        MockHttpServletResponse response = authorize("/api/plans", token);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String token = JwtTokenServiceTest.token(
                SECRET, "travel-planning-agent", NOW.minusSeconds(120), NOW.minusSeconds(60));

        MockHttpServletResponse response = authorize("/api/plans", token);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void tokenFromWrongIssuerReturns401() throws Exception {
        String token = JwtTokenServiceTest.token(
                SECRET, "another-issuer", NOW, NOW.plusSeconds(3600));

        MockHttpServletResponse response = authorize("/api/plans", token);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void userRoleAccessingAdminEndpointReturns403() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "ordinary-user", Role.USER)).value();

        MockHttpServletResponse response = authorize("/api/admin/knowledge-documents", token);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void userRoleCannotBypassAdminCheckWithContextPath() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "ordinary-user", Role.USER)).value();
        MockHttpServletRequest request = request("/travel/api/admin/knowledge-documents", token);
        request.setContextPath("/travel");
        request.setServletPath("/api/admin/knowledge-documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void userRoleCannotBypassAdminCheckWithMatrixParameter() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "ordinary-user", Role.USER)).value();

        MockHttpServletResponse response = authorize(
                "/api/admin;source=test/knowledge-documents", token);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void userRoleCannotBypassAdminCheckWithServletMappingPrefix() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(7, "ordinary-user", Role.USER)).value();
        MockHttpServletRequest request = request("/travel/api/admin/knowledge-documents", token);
        request.setServletPath("/travel");
        request.setPathInfo("/api/admin/knowledge-documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void adminRoleCanAccessAdminEndpointAndContextIsAlwaysClearedAfterCompletion() throws Exception {
        String token = tokenService.issueToken(new CurrentUser(8, "admin-user", Role.ADMIN)).value();
        MockHttpServletRequest request = request("/api/admin/knowledge-documents", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(CurrentUserContext.get()).contains(
                new CurrentUser(8, "admin-user", Role.ADMIN));

        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("probe"));
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void loginAndCaptchaEndpointsArePublic() throws Exception {
        assertThat(interceptor.preHandle(request("/api/auth/login", null),
                new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(request("/api/auth/captcha", null),
                new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void corsPreflightIsAllowedWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/admin/knowledge-documents/42/status");
        request.setRequestURI("/api/admin/knowledge-documents/42/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(CurrentUserContext.get()).isEmpty();
    }

    @Test
    void loginEndpointRemainsPublicWithServletMappingPrefix() throws Exception {
        MockHttpServletRequest request = request("/travel/api/auth/login", null);
        request.setServletPath("/travel");
        request.setPathInfo("/api/auth/login");

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void asyncHandlingAlwaysClearsCurrentUserContext() throws Exception {
        CurrentUserContext.set(new CurrentUser(8, "admin-user", Role.ADMIN));

        assertThat(interceptor).isInstanceOf(AsyncHandlerInterceptor.class);
        ((AsyncHandlerInterceptor) interceptor).afterConcurrentHandlingStarted(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(CurrentUserContext.get()).isEmpty();
    }

    private MockHttpServletResponse authorize(String uri, String token) throws Exception {
        MockHttpServletRequest request = request(uri, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());
        assertThat(allowed).isFalse();
        return response;
    }

    private MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static String encodedSecret(String raw) {
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
