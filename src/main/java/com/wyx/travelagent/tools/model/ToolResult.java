package com.wyx.travelagent.tools.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ToolResult<T>(
        boolean success,
        String provider,
        LocalDateTime retrievedAt,
        T data,
        String errorCode,
        String errorMessage
) {
    public ToolResult {
        requireNonBlank(provider, "provider");
        Objects.requireNonNull(retrievedAt, "retrievedAt");

        if (success) {
            Objects.requireNonNull(data, "data");
            data = defensiveCopyIfList(data);
            if (errorCode != null || errorMessage != null) {
                throw new IllegalArgumentException("successful result cannot contain error information");
            }
        } else {
            if (data != null) {
                throw new IllegalArgumentException("failed result cannot contain data");
            }
            requireNonBlank(errorCode, "errorCode");
            if (errorMessage != null && errorMessage.isBlank()) {
                throw new IllegalArgumentException("errorMessage must not be blank");
            }
        }
    }

    public static <T> ToolResult<T> success(String provider, LocalDateTime retrievedAt, T data) {
        return new ToolResult<>(true, provider, retrievedAt, data, null, null);
    }

    public static <T> ToolResult<T> failure(
            String provider,
            LocalDateTime retrievedAt,
            String errorCode,
            String errorMessage
    ) {
        return new ToolResult<>(false, provider, retrievedAt, null, errorCode, errorMessage);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T defensiveCopyIfList(T data) {
        if (data instanceof List<?> list) {
            return (T) List.copyOf(list);
        }
        return data;
    }
}
