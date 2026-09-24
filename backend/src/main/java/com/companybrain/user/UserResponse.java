package com.companybrain.user;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        Role role,
        DepartmentResponse department,
        Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                DepartmentResponse.from(user.getDepartment()), user.getCreatedAt());
    }
}
