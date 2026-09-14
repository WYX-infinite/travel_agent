package com.wyx.travelagent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationSafetyTest {

    @Test
    void committedConfigurationSupportsLocalFallbackWithoutLiteralSecrets() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String gitignore = Files.readString(Path.of(".gitignore"));

        assertThat(yaml.contains("${DASHSCOPE_API_KEY:${travel.local-secrets.dashscope-api-key:}}"))
            .as("DashScope API key must support environment and local fallback")
            .isTrue();
        assertThat(yaml.contains("${AMAP_WEB_API_KEY:${travel.local-secrets.amap-web-api-key:}}"))
            .as("AMap API key must support environment and local fallback")
            .isTrue();
        assertThat(yaml.contains("${TRAVEL_JWT_SECRET:${travel.local-secrets.jwt-secret:}}"))
            .as("JWT secret must support environment and local fallback")
            .isTrue();
        assertThat(yaml.contains("host: ${TRAVEL_REDIS_HOST:192.168.154.130}"))
            .as("Redis host must support the authenticated local fallback")
            .isTrue();
        assertThat(yaml.contains("port: ${TRAVEL_REDIS_PORT:6379}"))
            .as("Redis port must support environment and local fallback")
            .isTrue();
        assertThat(yaml.contains("password: ${TRAVEL_REDIS_PASSWORD:}"))
            .as("Redis password must support an environment-only secret")
            .isTrue();
        assertThat(yaml.contains("api-key: sk-"))
            .as("committed YAML must not contain a literal API key")
            .isFalse();
        assertThat(gitignore.contains("application-local.yml"))
            .as("local configuration must be ignored")
            .isTrue();
    }
}
