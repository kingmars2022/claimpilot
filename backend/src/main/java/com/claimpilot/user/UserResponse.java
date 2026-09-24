package com.claimpilot.user;

public record UserResponse(Long id, String username, String displayName) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
