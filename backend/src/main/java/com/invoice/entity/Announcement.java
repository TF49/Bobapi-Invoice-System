package com.invoice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告实体
 */
@Data
@TableName("announcement")
public class Announcement {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /**
     * Markdown 正文（前端渲染时做 XSS 过滤）
     */
    private String content;

    /**
     * 分类：FEATURE / MAINTENANCE / IMPORTANT / GENERAL
     */
    private String category;

    /**
     * 状态：DRAFT / PUBLISHED / OFFLINE
     */
    private String status;

    /**
     * 推送版本：每次发布/重新推送 +1
     */
    private Integer pushVersion;

    private LocalDateTime publishedAt;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
