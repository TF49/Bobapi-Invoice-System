-- 发票管理系统数据库初始化脚本
-- 已整合 Flyway V1-V22 的最终完整数据库结构，适用于全新安装或彻底重置。
-- 警告：执行本脚本会删除 invoice_system 数据库及其中的全部数据。
-- 推荐部署方式：创建空数据库后直接启动后端，由 Flyway 自动按版本迁移初始化。
-- 如果手工执行本脚本，首次启动后端时需将 SPRING_FLYWAY_BASELINE_VERSION 临时设为 22。

DROP DATABASE IF EXISTS `invoice_system`;

CREATE DATABASE `invoice_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `invoice_system`;

-- 1. 用户表
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

-- 2. 发票申请批次表
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
    KEY `idx_user_created_at` (`user_id`, `created_at`),
    CONSTRAINT `fk_batch_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='发票申请批次表';

-- 3. 发票表
CREATE TABLE `invoice` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '发票ID',
    `company_name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `tax_number` VARCHAR(100) DEFAULT NULL COMMENT '税号',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '开票金额',
    `invoice_type` VARCHAR(100) NOT NULL DEFAULT '技术服务费' COMMENT '开票类型',
    `invoice_category` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '发票票种：NORMAL-增值税普通发票，VAT_SPECIAL-增值税专用发票',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `out_trade_no` VARCHAR(100) DEFAULT NULL COMMENT '外部商户订单号（用于OpenAPI对接）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待开票，COMPLETED-已开票',
    `is_processed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户已处理标记：0-未处理，1-已处理',
    `file_path` VARCHAR(500) DEFAULT NULL COMMENT '服务端存储文件名',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始发票文件名',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '单条创建请求幂等键；批量申请明细为空',
    `batch_id` BIGINT DEFAULT NULL COMMENT '批次ID（批量申请时关联）',
    `batch_row_number` INT DEFAULT NULL COMMENT '批次内原始行号（用于审计）',
    `submission_type` VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '提交方式：API-API提交，MANUAL-网页手动提交，UNKNOWN-历史无法确认',
    `is_red_flushed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已红冲：0-否，1-是',
    `red_flushed_at` DATETIME DEFAULT NULL COMMENT '红冲时间',
    `red_flush_remark` VARCHAR(500) DEFAULT NULL COMMENT '红冲原因备注',
    `red_flush_operator_id` BIGINT DEFAULT NULL COMMENT '红冲操作人ID',
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
    KEY `idx_invoice_red_flush` (`is_red_flushed`),
    CONSTRAINT `fk_invoice_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='发票表';

-- 4. 用户额度表
CREATE TABLE `user_quota` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '额度记录ID',
    `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    `balance` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '当前剩余额度',
    `total_recharged` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '总充值金额',
    `total_deducted` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '总扣除金额',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_balance` (`balance`),
    CONSTRAINT `fk_user_quota_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户额度表';

-- 5. 用户额度变更历史表
CREATE TABLE `user_quota_transaction` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '交易ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `transaction_type` VARCHAR(20) NOT NULL COMMENT '交易类型：RECHARGE-充值，DEDUCT-扣除，ADJUST-调整',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '管理员额度操作幂等键',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '变更金额（正数表示增加，负数表示减少）',
    `balance_before` DECIMAL(12, 2) NOT NULL COMMENT '变更前余额',
    `balance_after` DECIMAL(12, 2) NOT NULL COMMENT '变更后余额',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID（管理员操作时记录）',
    `operator_type` VARCHAR(20) DEFAULT NULL COMMENT '操作人类型：ADMIN-管理员，SYSTEM-系统',
    `invoice_id` BIGINT DEFAULT NULL COMMENT '关联的发票ID（扣除类型时记录）',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_transaction_type` (`transaction_type`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_user_time` (`user_id`, `created_at`),
    UNIQUE KEY `uk_quota_transaction_user_idempotency` (`user_id`, `idempotency_key`),
    CONSTRAINT `fk_quota_transaction_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_quota_transaction_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户额度变更历史表';

-- 6. 充值申请表
CREATE TABLE `recharge_request` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `fee_amount` DECIMAL(10, 2) DEFAULT NULL COMMENT '用户支付的手续费',
    `amount` DECIMAL(10, 2) NOT NULL COMMENT '按手续费计算的申请额度',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '用户创建申请的幂等键',
    `screenshot_url` VARCHAR(512) DEFAULT NULL COMMENT '充值截图URL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '申请状态：PENDING-待审核，APPROVED-已通过，REJECTED-已拒绝',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '用户备注',
    `admin_remark` VARCHAR(500) DEFAULT NULL COMMENT '管理员审核备注',
    `reviewed_by` BIGINT DEFAULT NULL COMMENT '审核管理员ID',
    `reviewed_at` DATETIME DEFAULT NULL COMMENT '审核时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    UNIQUE KEY `uk_recharge_request_user_idempotency` (`user_id`, `idempotency_key`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='充值申请表';

-- 7. 供应商结算记录表
CREATE TABLE `supplier_settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '结算记录ID',
    `settlement_amount` DECIMAL(12, 2) NOT NULL COMMENT '本次结算金额',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '结算备注',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID（管理员）',
    `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人用户名（冗余存储）',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '结算请求幂等键',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_operator_id` (`operator_id`),
    INDEX `idx_created_at` (`created_at`),
    UNIQUE INDEX `uk_settlement_idempotency` (`idempotency_key`),
    CONSTRAINT `fk_supplier_settlement_user` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='供应商结算记录表';

-- 默认账号：admin（高强度密码），user / user123
INSERT INTO `user` (`id`, `username`, `password`, `role`) VALUES
    (1, 'admin', '$2b$10$03QLUNY7Yr9xBWJwbugxdONqzssH03qsdhG2XBiG9.RxHQjniRkTG', 'ADMIN'),
    (2, 'user', '$2a$10$8ORsuwbOGeGgarcH2nik8uvA1c8X4ah98zVMgpkkwIg.6PImYIRZ2', 'USER');

-- 初始化默认用户额度记录
INSERT IGNORE INTO `user_quota` (`user_id`, `balance`, `total_recharged`, `total_deducted`) VALUES
    (1, 0.00, 0.00, 0.00),
    (2, 0.00, 0.00, 0.00);
