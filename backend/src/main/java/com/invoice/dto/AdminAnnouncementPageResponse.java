package com.invoice.dto;

import java.util.List;

public record AdminAnnouncementPageResponse(
        List<AdminAnnouncementResponse> records,
        long total,
        long page,
        long pageSize,
        long totalPages,
        AnnouncementStats stats
) {
}
