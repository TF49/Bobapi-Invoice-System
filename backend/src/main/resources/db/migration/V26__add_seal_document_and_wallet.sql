-- V26: 新增盖章文件管理、独立盖章钱包与充值类型支持

-- 1. 用户盖章独立额度表（10元/次）
CREATE TABLE IF NOT EXISTS `user_seal_quota` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '盖章额度ID',
    `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    `balance` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '当前可用盖章额度(元)',
    `total_recharged` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '累计充值金额(元)',
    `total_deducted` DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '累计扣除金额(元)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_user_seal_quota_user` (`user_id`),
    CONSTRAINT `fk_user_seal_quota_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户盖章独立额度表';

-- 2. 用户盖章额度变更流水表
CREATE TABLE IF NOT EXISTS `user_seal_transaction` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '交易ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `transaction_type` VARCHAR(20) NOT NULL COMMENT '交易类型：RECHARGE-充值，DEDUCT-扣除，REFUND-退还，ADJUST-调整',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '变更金额（正数表示增加，负数表示减少）',
    `balance_before` DECIMAL(12, 2) NOT NULL COMMENT '变更前余额',
    `balance_after` DECIMAL(12, 2) NOT NULL COMMENT '变更后余额',
    `document_id` BIGINT COMMENT '关联盖章文件ID',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_type` VARCHAR(20) COMMENT '操作人类型：ADMIN-管理员，SYSTEM-系统，USER-用户',
    `remark` VARCHAR(255) COMMENT '备注说明',
    `idempotency_key` VARCHAR(64) COMMENT '幂等键',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_ust_user` (`user_id`),
    INDEX `idx_ust_type` (`transaction_type`),
    INDEX `idx_ust_created` (`created_at`),
    CONSTRAINT `fk_ust_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户盖章额度变更流水表';

-- 3. 盖章文件记录表
CREATE TABLE IF NOT EXISTS `seal_document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '盖章文件ID',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `source_file_name` VARCHAR(255) NOT NULL COMMENT '原始待盖章文件名',
    `source_file_path` VARCHAR(512) NOT NULL COMMENT '原始文件存储路径',
    `source_file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '原始文件大小(字节)',
    `source_file_ext` VARCHAR(50) COMMENT '原始文件扩展名',
    `sealed_file_name` VARCHAR(255) COMMENT '盖章交付文件名',
    `sealed_file_path` VARCHAR(512) COMMENT '盖章交付文件路径',
    `sealed_file_size` BIGINT DEFAULT 0 COMMENT '盖章交付文件大小(字节)',
    `fee_amount` DECIMAL(10, 2) NOT NULL DEFAULT 10.00 COMMENT '服务费金额(固定10.00)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待盖章，COMPLETED-已完成，REJECTED-已驳回，CANCELLED-已取消',
    `reject_reason` VARCHAR(500) COMMENT '驳回原因',
    `operator_id` BIGINT COMMENT '盖章交付操作人ID',
    `is_processed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户标记处理：0-未处理，1-已处理',
    `completed_at` DATETIME COMMENT '盖章完成交付时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_sd_user` (`user_id`),
    INDEX `idx_sd_status` (`status`),
    INDEX `idx_sd_created` (`created_at`),
    CONSTRAINT `fk_sd_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盖章文件管理表';

-- 4. 充值申请表新增充值类型字段（INVOICE-发票额度，SEAL-盖章额度）
ALTER TABLE recharge_request
    ADD COLUMN request_type VARCHAR(20) NOT NULL DEFAULT 'INVOICE'
        COMMENT '充值类型：INVOICE-发票额度，SEAL-盖章钱包' AFTER user_id,
    ADD INDEX idx_request_type (request_type);

-- 5. 为现有存量用户初始化盖章独立额度记录
INSERT IGNORE INTO `user_seal_quota` (`user_id`, `balance`, `total_recharged`, `total_deducted`)
SELECT `id`, 0.00, 0.00, 0.00 FROM `user` WHERE `deleted` = 0;
