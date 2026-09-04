package com.wyx.travelagent.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class CaptchaService {
    private static final String KEY_PREFIX = "travel:captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "redis.call('DEL', KEYS[1]); "
                    + "if value == ARGV[1] then return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String captchaId, String code) {
        requireText(captchaId, "captchaId");
        requireText(code, "code");
        redisTemplate.opsForValue().set(key(captchaId), code, TTL);
    }

    public boolean verifyAndDelete(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        Long result = redisTemplate.execute(
                VERIFY_AND_DELETE_SCRIPT,
                Collections.singletonList(key(captchaId)),
                code);
        return Long.valueOf(1L).equals(result);
    }

    private String key(String captchaId) {
        return KEY_PREFIX + captchaId;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
