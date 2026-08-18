package com.invoice.dto;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserResponse> users,
        long total,
        long page,
        long pageSize,
        long totalPages,
        AdminUserStats stats
) {
}
