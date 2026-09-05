package com.wyx.travelagent.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wyx.travelagent.common.api.ApiError;
import com.wyx.travelagent.common.api.ApiResponse;
import com.wyx.travelagent.entity.User;
import com.wyx.travelagent.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final int CAPTCHA_WIDTH = 120;
    private static final int CAPTCHA_HEIGHT = 40;
    private static final int CAPTCHA_LENGTH = 4;

    private final UserMapper userMapper;
    private final CaptchaService captchaService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthController(UserMapper userMapper, CaptchaService captchaService, BCryptPasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userMapper = userMapper;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaResponse>> captcha() throws IOException {
        String captchaId = UUID.randomUUID().toString();
        String code = randomDigits();
        captchaService.save(captchaId, code);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(new CaptchaResponse(captchaId, renderCaptcha(code), 300)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request) {
        if (!captchaService.verifyAndDelete(request.captchaId(), request.captchaCode())) {
            return authenticationFailure();
        }

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return authenticationFailure();
        }

        Role role = user.getRole() == null ? Role.USER : user.getRole();
        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), role);
        JwtTokenService.IssuedToken issued = tokenService.issueToken(currentUser);
        UserProfile profile = new UserProfile(user.getId(), user.getName(), user.getUsername(), role);
        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(issued.value(), "Bearer", issued.expiresAt(), profile)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> me() {
        CurrentUser currentUser = CurrentUserContext.get().orElse(null);
        if (currentUser == null) {
            return unauthorized("authentication required");
        }
        User user = userMapper.selectById(currentUser.userId());
        if (user == null) {
            return unauthorized("current user no longer exists");
        }
        Role role = user.getRole() == null ? currentUser.role() : user.getRole();
        return ResponseEntity.ok(ApiResponse.ok(new UserProfile(user.getId(), user.getName(), user.getUsername(), role)));
    }

    private ResponseEntity<ApiResponse<?>> authenticationFailure() {
        return unauthorized("invalid username, password, or captcha");
    }

    private ResponseEntity<ApiResponse<?>> unauthorized(String message) {
        ApiError error = new ApiError("UNAUTHORIZED", message, null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(error));
    }

    private String randomDigits() {
        StringBuilder code = new StringBuilder(CAPTCHA_LENGTH);
        for (int index = 0; index < CAPTCHA_LENGTH; index++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    private String renderCaptcha(String code) throws IOException {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(244, 247, 250));
            graphics.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            graphics.setColor(new Color(32, 61, 86));
            graphics.drawString(code, 28, 29);
            graphics.setColor(new Color(120, 140, 160));
            for (int index = 0; index < 8; index++) {
                graphics.drawLine(secureRandom.nextInt(CAPTCHA_WIDTH), secureRandom.nextInt(CAPTCHA_HEIGHT),
                        secureRandom.nextInt(CAPTCHA_WIDTH), secureRandom.nextInt(CAPTCHA_HEIGHT));
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password, @NotBlank String captchaId, @NotBlank String captchaCode) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserProfile user) {
    }

    public record CaptchaResponse(String captchaId, String imageDataUrl, int expiresInSeconds) {
    }
}
