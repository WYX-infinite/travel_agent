package com.wyx.travelagent.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private final Map<String, String> values = new HashMap<>();
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CaptchaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenAnswer(invocation -> {
                    RedisScript<?> script = invocation.getArgument(0);
                    List<String> keys = invocation.getArgument(1);
                    String key = keys.getFirst();
                    String expected = invocation.getArgument(2);
                    String actual = values.get(key);
                    String source = script.getScriptAsString();
                    boolean deletesBeforeComparison = source.indexOf("DEL") < source.indexOf("==");
                    if (deletesBeforeComparison) {
                        values.remove(key);
                    }
                    if (expected.equals(actual)) {
                        if (!deletesBeforeComparison) {
                            values.remove(key);
                        }
                        return 1L;
                    }
                    return 0L;
                });
        service = new CaptchaService(redisTemplate);
    }

    @Test
    void captchasAreIsolatedByIdAndConsumedOnce() {
        service.save("captcha-a", "1234");

        assertThat(service.verifyAndDelete("captcha-b", "1234")).isFalse();
        assertThat(service.verifyAndDelete("captcha-a", "1234")).isTrue();
        assertThat(service.verifyAndDelete("captcha-a", "1234")).isFalse();
    }

    @Test
    void captchaUsesNamespacedKeyAndFiveMinuteTtl() {
        service.save("captcha-a", "1234");

        verify(valueOperations).set(
                eq("travel:captcha:captcha-a"), eq("1234"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void wrongAnswerAlsoConsumesCaptcha() {
        service.save("captcha-a", "1234");

        assertThat(service.verifyAndDelete("captcha-a", "9999")).isFalse();
        assertThat(service.verifyAndDelete("captcha-a", "1234")).isFalse();
    }
}
