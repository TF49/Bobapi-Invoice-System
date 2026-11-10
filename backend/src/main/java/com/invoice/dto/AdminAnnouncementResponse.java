package com.invoice.dto;

import com.invoice.entity.Announcement;

import java.time.LocalDateTime;

public record AdminAnnouncementResponse(
        Long id,
        String title,
        String content,
        String category,
        String status,
        Integer pushVersion,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long readCount
) {
    public static AdminAnnouncementResponse from(Announcement a, long readCount) {
        return new AdminAnnouncementResponse(a.getId(), a.getTitle(), a.getContent(), a.getCategory(),
                a.getStatus(), a.getPushVersion(), a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt(), readCount);
    }
}
