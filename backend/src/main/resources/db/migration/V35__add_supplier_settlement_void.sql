-- V35: 供应商结算记录支持「作废」纠错（选错通道 / 填错金额时作废后重新登记）。
-- 作废记录保留在历史中展示，但不再计入任何已结算汇总（总额、分通道、月度走势）。
-- 兼容已通过 init.sql 建好这些列的数据库。
SET @db_name = DATABASE();

SET @add_voided_at_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'voided_at'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `voided_at` DATETIME DEFAULT NULL COMMENT ''作废时间，NULL 表示有效'' AFTER `idempotency_key`',
    'SELECT 1'
);
PREPARE add_voided_at_stmt FROM @add_voided_at_sql;
EXECUTE add_voided_at_stmt;
DEALLOCATE PREPARE add_voided_at_stmt;

SET @add_voided_by_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'voided_by'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `voided_by` BIGINT DEFAULT NULL COMMENT ''作废操作人ID'' AFTER `voided_at`',
    'SELECT 1'
);
PREPARE add_voided_by_stmt FROM @add_voided_by_sql;
EXECUTE add_voided_by_stmt;
DEALLOCATE PREPARE add_voided_by_stmt;

SET @add_voided_by_name_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'voided_by_name'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `voided_by_name` VARCHAR(100) DEFAULT NULL COMMENT ''作废操作人用户名（冗余存储）'' AFTER `voided_by`',
    'SELECT 1'
);
PREPARE add_voided_by_name_stmt FROM @add_voided_by_name_sql;
EXECUTE add_voided_by_name_stmt;
DEALLOCATE PREPARE add_voided_by_name_stmt;

SET @add_void_reason_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'void_reason'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `void_reason` VARCHAR(200) DEFAULT NULL COMMENT ''作废原因'' AFTER `voided_by_name`',
    'SELECT 1'
);
PREPARE add_void_reason_stmt FROM @add_void_reason_sql;
EXECUTE add_void_reason_stmt;
DEALLOCATE PREPARE add_void_reason_stmt;
