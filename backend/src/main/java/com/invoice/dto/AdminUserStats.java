package com.invoice.dto;

public record AdminUserStats(
        long totalUsers,
        long enabledUsers,
        long disabledUsers,
        long adminUsers
) {
}
