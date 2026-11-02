package com.bee.shop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AuthDtos {
    private AuthDtos() {}

    public static final int MIN_PASSWORD = 10;

    public record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 200) String password) {}

    public record LoginResponse(String token, OffsetDateTime expiresAt, UserResponse user) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = MIN_PASSWORD, max = 200) String newPassword) {}

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "hanya huruf, angka, titik, minus, underscore") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = MIN_PASSWORD, max = 200) String password,
            @NotNull AdminRole role) {}

    public record UpdateUserRequest(@Size(max = 120) String displayName, AdminRole role, Boolean active,
                                    @Size(min = MIN_PASSWORD, max = 200) String newPassword) {}

    public record UserResponse(Long id, String username, String displayName, AdminRole role, boolean active,
                               OffsetDateTime lastLoginAt, OffsetDateTime lockedUntil, OffsetDateTime createdAt) {
        public static UserResponse from(AdminUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive(),
                    u.getLastLoginAt(), u.getLockedUntil(), u.getCreatedAt());
        }
    }
}
