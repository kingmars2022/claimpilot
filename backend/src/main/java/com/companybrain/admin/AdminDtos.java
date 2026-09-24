package com.companybrain.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.companybrain.user.Role;

/** Request bodies for the admin API. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateUser(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "may contain only letters, digits, dots, dashes and underscores") String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role,
            Long departmentId) {
    }

    /** A blank password keeps the current one. */
    public record UpdateUser(
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 100) String password,
            @NotNull Role role,
            Long departmentId) {
    }

    public record CreateDepartment(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_]+",
                    message = "may contain only letters, digits and underscores") String code,
            @NotBlank @Size(max = 100) String name) {
    }
}
