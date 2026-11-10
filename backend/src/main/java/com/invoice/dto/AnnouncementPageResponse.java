package com.invoice.dto;

import java.util.List;

public record AnnouncementPageResponse(
        List<AnnouncementResponse> records,
        long total,
        long page,
        long pageSize,
        long totalPages
) {
}
