package com.wyx.travelagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationInterceptor implements AsyncHandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;

    public JwtAuthenticationInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        CurrentUserContext.clear();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String requestPath = UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
        if (isPublicAuthenticationEndpoint(requestPath)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "authentication required");
            return false;
        }

        final CurrentUser currentUser;
        try {
            currentUser = tokenService.parse(authorization.substring(BEARER_PREFIX.length()));
        } catch (RuntimeException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "invalid or expired access token");
            return false;
        }

        if (isAdministratorEndpoint(requestPath) && currentUser.role() != Role.ADMIN) {
            CurrentUserContext.clear();
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "administrator role required");
            return false;
        }

        CurrentUserContext.set(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        CurrentUserContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        CurrentUserContext.clear();
    }

    private boolean isPublicAuthenticationEndpoint(String requestUri) {
        return "/api/auth/login".equals(requestUri) || "/api/auth/captcha".equals(requestUri);
    }

    private boolean isAdministratorEndpoint(String requestPath) {
        return "/api/admin".equals(requestPath) || requestPath.startsWith("/api/admin/");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\""
                + code + "\",\"message\":\"" + message + "\"}}");
    }
}
