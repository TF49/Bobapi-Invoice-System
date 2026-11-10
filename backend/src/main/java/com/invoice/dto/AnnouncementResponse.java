package com.invoice.dto;

import com.invoice.entity.Announcement;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        String title,
        String content,
        String category,
        String status,
        Integer pushVersion,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getContent(), a.getCategory(),
                a.getStatus(), a.getPushVersion(), a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
