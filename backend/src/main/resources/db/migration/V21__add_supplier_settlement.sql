CREATE TABLE IF NOT EXISTS `supplier_settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '结算记录ID',
    `settlement_amount` DECIMAL(12, 2) NOT NULL COMMENT '本次结算金额',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '结算备注',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID（管理员）',
    `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人用户名（冗余存储）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_operator_id` (`operator_id`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_supplier_settlement_user` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商结算记录表';
