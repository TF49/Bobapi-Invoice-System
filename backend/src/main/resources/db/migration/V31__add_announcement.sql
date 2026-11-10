-- V31: 新增系统公告（弹窗通知）与用户已读记录

-- 1. 系统公告表
CREATE TABLE IF NOT EXISTS `announcement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID',
    `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告正文（Markdown）',
    `category` VARCHAR(20) NOT NULL DEFAULT 'GENERAL' COMMENT '分类：FEATURE-新功能，MAINTENANCE-系统维护，IMPORTANT-重要通知，GENERAL-一般通知',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿，PUBLISHED-已发布，OFFLINE-已下线',
    `push_version` INT NOT NULL DEFAULT 0 COMMENT '推送版本：每次发布/重新推送 +1，用户已读版本小于该值时会再次弹窗',
    `published_at` DATETIME NULL COMMENT '最近一次发布或重新推送时间',
    `created_by` BIGINT NOT NULL COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_announcement_status_published` (`status`, `deleted`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- 2. 公告已读记录表
CREATE TABLE IF NOT EXISTS `announcement_read` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `announcement_id` BIGINT NOT NULL COMMENT '公告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_version` INT NOT NULL COMMENT '已读时公告的推送版本',
    `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    UNIQUE KEY `uk_announcement_read_user` (`announcement_id`, `user_id`),
    INDEX `idx_announcement_read_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告已读记录表';
