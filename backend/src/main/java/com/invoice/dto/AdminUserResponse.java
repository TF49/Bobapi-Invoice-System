package com.invoice.dto;

import com.invoice.entity.User;

import java.time.LocalDateTime;
import java.util.Objects;

public record AdminUserResponse(
        Long id,
        String username,
        String role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean self
) {
    public static AdminUserResponse from(User user, Long currentUserId) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                Objects.equals(user.getId(), currentUserId)
        );
    }
}
