package com.wyx.travelagent.auth;

import java.util.Optional;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser currentUser) {
        CURRENT_USER.set(currentUser);
    }

    public static Optional<CurrentUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
