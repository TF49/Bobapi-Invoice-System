-- V33: 新增发票流转事件表（税号数据流），并为税号检索补充索引
-- 兼容已通过 init.sql 建好该表 / 索引的数据库。

CREATE TABLE IF NOT EXISTS `invoice_event` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件ID',
    `invoice_id` BIGINT NOT NULL COMMENT '发票ID',
    `user_id` BIGINT NOT NULL COMMENT '发票所属用户ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型：SUBMITTED/QUOTA_DEDUCTED/ADMIN_UPDATED/ISSUED/FILE_REPLACED/CANCELLED/RED_FLUSH_APPLIED/RED_FLUSH_CONFIRMED/RED_FLUSH_REJECTED',
    `operator_type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '操作方：USER/ADMIN/API/SYSTEM',
    `operator_id` BIGINT NULL COMMENT '操作人ID',
    `quota_delta` DECIMAL(15,2) NULL COMMENT '本事件对该发票额度的影响（负数扣除，正数退还）',
    `balance_after` DECIMAL(15,2) NULL COMMENT '额度变动后用户余额（批量合并扣除时为空）',
    `quota_transaction_id` BIGINT NULL COMMENT '关联额度流水ID',
    `detail` VARCHAR(500) NULL COMMENT '事件说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    INDEX `idx_invoice_event_invoice_time` (`invoice_id`, `created_at`),
    INDEX `idx_invoice_event_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票流转事件表';

SET @db_name = DATABASE();

SET @add_tax_index_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'invoice'
          AND BINARY INDEX_NAME = BINARY 'idx_invoice_user_tax_number'
    ),
    'ALTER TABLE `invoice` ADD INDEX `idx_invoice_user_tax_number` (`user_id`, `tax_number`)',
    'SELECT 1'
);
PREPARE add_tax_index_stmt FROM @add_tax_index_sql;
EXECUTE add_tax_index_stmt;
DEALLOCATE PREPARE add_tax_index_stmt;
