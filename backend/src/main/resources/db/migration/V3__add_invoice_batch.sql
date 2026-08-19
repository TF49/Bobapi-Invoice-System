-- 批量导入批次表
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

-- 给发票表增加批次关联字段
ALTER TABLE `invoice`
    ADD COLUMN `batch_id` BIGINT DEFAULT NULL COMMENT '批次ID（批量申请时关联）' AFTER `idempotency_key`,
    ADD COLUMN `batch_row_number` INT DEFAULT NULL COMMENT '批次内原始行号（用于审计）' AFTER `batch_id`,
    ADD KEY `idx_batch_id` (`batch_id`);