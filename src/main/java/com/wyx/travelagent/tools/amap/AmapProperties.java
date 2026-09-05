package com.wyx.travelagent.tools.amap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties("amap")
public record AmapProperties(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
    private static final String DEFAULT_BASE_URL = "https://restapi.amap.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    public AmapProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? "" : apiKey;
        connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_CONNECT_TIMEOUT, "connectTimeout");
        readTimeout = positiveOrDefault(readTimeout, DEFAULT_READ_TIMEOUT, "readTimeout");
    }

    @Override
    public String toString() {
        return "AmapProperties[baseUrl=" + baseUrl
                + ", apiKey=<redacted>, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue, String field) {
        Duration resolved = Objects.requireNonNullElse(value, defaultValue);
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }
}
