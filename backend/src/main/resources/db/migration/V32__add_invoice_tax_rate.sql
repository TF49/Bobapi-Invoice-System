-- V32: 发票申请新增「税率 / 是否免税」字段：EXEMPT-免税（默认）、1%、3%、6%
-- 兼容已通过 init.sql 建好该列的数据库。
SET @db_name = DATABASE();

SET @add_tax_rate_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'invoice'
          AND BINARY COLUMN_NAME = BINARY 'tax_rate'
    ),
    'ALTER TABLE `invoice` ADD COLUMN `tax_rate` VARCHAR(10) NOT NULL DEFAULT ''EXEMPT'' COMMENT ''税率：EXEMPT-免税（默认），1%，3%，6%'' AFTER `seller_company`',
    'SELECT 1'
);
PREPARE add_tax_rate_stmt FROM @add_tax_rate_sql;
EXECUTE add_tax_rate_stmt;
DEALLOCATE PREPARE add_tax_rate_stmt;
