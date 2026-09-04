package com.wyx.travelagent.auth;

import java.util.Objects;

public record CurrentUser(Integer userId, String username, Role role) {
    public CurrentUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }
}
