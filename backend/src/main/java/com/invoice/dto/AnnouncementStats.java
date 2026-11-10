package com.invoice.dto;

/**
 * 公告管理页统计（不受列表筛选条件影响）
 */
public record AnnouncementStats(
        long total,
        long published,
        long draft,
        long offline
) {
}
