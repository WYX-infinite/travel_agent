package com.wyx.travelagent.auth;

import com.wyx.travelagent.entity.User;

public record UserProfile(Integer id, String name, String username, Role role) {
    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }
}
