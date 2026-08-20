-- 创建用户额度表
CREATE TABLE IF NOT EXISTS `user_quota` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户额度表';

-- 创建额度变更历史表
CREATE TABLE IF NOT EXISTS `user_quota_transaction` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '交易ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `transaction_type` VARCHAR(20) NOT NULL COMMENT '交易类型：RECHARGE-充值，DEDUCT-扣除，ADJUST-调整',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '变更金额（正数表示增加，负数表示减少）',
    `balance_before` DECIMAL(12, 2) NOT NULL COMMENT '变更前余额',
    `balance_after` DECIMAL(12, 2) NOT NULL COMMENT '变更后余额',
    `operator_id` BIGINT COMMENT '操作人ID（管理员操作时记录）',
    `operator_type` VARCHAR(20) COMMENT '操作人类型：ADMIN-管理员，SYSTEM-系统',
    `invoice_id` BIGINT COMMENT '关联的发票ID（扣除类型时记录）',
    `remark` VARCHAR(200) COMMENT '备注说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_transaction_type` (`transaction_type`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_quota_transaction_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_quota_transaction_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户额度变更历史表';

-- 为现有用户初始化额度记录（如果用户表已有数据）
INSERT IGNORE INTO `user_quota` (`user_id`, `balance`, `total_recharged`, `total_deducted`)
SELECT `id`, 0.00, 0.00, 0.00 FROM `user` WHERE `deleted` = 0;