package com.wyx.travelagent.auth;

import com.wyx.travelagent.entity.User;
import com.wyx.travelagent.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthControllerTest {

    private UserMapper userMapper;
    private CaptchaService captchaService;
    private BCryptPasswordEncoder passwordEncoder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        captchaService = mock(CaptchaService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        String secret = java.util.Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        JwtTokenService tokenService = new JwtTokenService(
                new JwtProperties(secret, "travel-planning-agent", Duration.ofHours(2)),
                Clock.fixed(Instant.parse("2026-09-04T06:00:00Z"), ZoneOffset.UTC));
        mockMvc = standaloneSetup(new AuthController(
                userMapper, captchaService, passwordEncoder, tokenService)).build();
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void loginUsesBcryptAndReturnsTokenWithSafeUserProfile() throws Exception {
        User user = user(1, "演示用户", "demo-user", passwordEncoder.encode("correct-password"), Role.USER);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(captchaService.verifyAndDelete("captcha-1", "4821")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "demo-user",
                                  "password": "correct-password",
                                  "captchaId": "captcha-1",
                                  "captchaCode": "4821"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.name").value("演示用户"))
                .andExpect(jsonPath("$.data.user.username").value("demo-user"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(content().string(not(containsString(user.getPassword()))));

        verify(captchaService).verifyAndDelete("captcha-1", "4821");
    }

    @Test
    void meResponseNeverContainsPasswordOrPasswordHash() throws Exception {
        User user = user(1, "演示用户", "demo-user", "stored-password-hash", Role.USER);
        when(userMapper.selectById(1)).thenReturn(user);
        CurrentUserContext.set(new CurrentUser(1, "demo-user", Role.USER));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("演示用户"))
                .andExpect(jsonPath("$.data.username").value("demo-user"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(content().string(not(containsString("stored-password-hash"))));
    }

    @Test
    void captchaResponseMustNotBeCached() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    private User user(int id, String name, String username, String password, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        return user;
    }
}
