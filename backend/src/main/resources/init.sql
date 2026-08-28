-- 发票管理系统数据库初始化脚本
-- 已整合 Flyway V1-V4 的最终数据库结构，适用于全新安装或彻底重置。
-- 警告：执行本脚本会删除 invoice_system 数据库及其中的全部数据。
-- 推荐部署方式：创建空数据库后直接启动后端，由 Flyway 按 V1-V4 自动初始化。
-- 如果手工执行本脚本，首次启动后端时需将 SPRING_FLYWAY_BASELINE_VERSION 临时设为 4。

DROP DATABASE IF EXISTS `invoice_system`;

CREATE DATABASE `invoice_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `invoice_system`;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户，ADMIN-管理员，INVOICE_CLERK-开票员',
    `api_key` VARCHAR(64) DEFAULT NULL COMMENT 'OpenAPI 调用密钥',
    `api_key_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'API Key是否启用：1-启用，0-禁用',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '管理员备注',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号状态：1-启用，0-禁用',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '认证版本，变更后使旧凭证失效',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_api_key` (`api_key`),
    KEY `idx_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_user_admin_state` (`role`, `enabled`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

CREATE TABLE `invoice` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '发票ID',
    `company_name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `tax_number` VARCHAR(100) DEFAULT NULL COMMENT '税号',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '开票金额',
    `invoice_type` VARCHAR(100) NOT NULL DEFAULT '技术服务费' COMMENT '开票类型',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `out_trade_no` VARCHAR(100) DEFAULT NULL COMMENT '外部商户订单号（用于OpenAPI对接）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待开票，COMPLETED-已开票',
    `is_processed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户已处理标记：0-未处理，1-已处理',
    `file_path` VARCHAR(500) DEFAULT NULL COMMENT '服务端存储文件名',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始发票文件名',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '单条创建请求幂等键；批量申请明细为空',
    `batch_id` BIGINT DEFAULT NULL COMMENT '批次ID（批量申请时关联）',
    `batch_row_number` INT DEFAULT NULL COMMENT '批次内原始行号（用于审计）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '实际完成开票时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invoice_user_idempotency` (`user_id`, `idempotency_key`),
    UNIQUE KEY `uk_invoice_user_out_trade_no` (`user_id`, `out_trade_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_out_trade_no` (`out_trade_no`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_status_completed_at` (`status`, `completed_at`),
    KEY `idx_batch_id` (`batch_id`),
    CONSTRAINT `fk_invoice_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='发票表';

CREATE TABLE `invoice_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '批次幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '请求内容SHA-256哈希',
    `total_count` INT NOT NULL COMMENT '批次总行数',
    `total_amount` DECIMAL(14, 2) NOT NULL COMMENT '批次总金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '批次状态：COMPLETED-已完成',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_user_created_at` (`user_id`, `created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='发票申请批次表';

-- 默认账号：admin（高强度密码），user / user123
INSERT INTO `user` (`username`, `password`, `role`) VALUES
    ('admin', '$2b$10$03QLUNY7Yr9xBWJwbugxdONqzssH03qsdhG2XBiG9.RxHQjniRkTG', 'ADMIN'),
    ('user', '$2a$10$8ORsuwbOGeGgarcH2nik8uvA1c8X4ah98zVMgpkkwIg.6PImYIRZ2', 'USER');
