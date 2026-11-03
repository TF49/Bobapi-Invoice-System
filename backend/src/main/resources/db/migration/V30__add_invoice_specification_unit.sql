-- V30: 发票申请新增「规格型号」「单位」两个选填字段（默认 NULL）
-- 兼容已通过 init.sql 建好该列的数据库。
SET @db_name = DATABASE();

SET @add_specification_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'invoice'
          AND BINARY COLUMN_NAME = BINARY 'specification'
    ),
    'ALTER TABLE `invoice` ADD COLUMN `specification` VARCHAR(100) DEFAULT NULL COMMENT ''规格型号（选填）'' AFTER `tax_number`',
    'SELECT 1'
);
PREPARE add_specification_stmt FROM @add_specification_sql;
EXECUTE add_specification_stmt;
DEALLOCATE PREPARE add_specification_stmt;

SET @add_unit_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'invoice'
          AND BINARY COLUMN_NAME = BINARY 'unit'
    ),
    'ALTER TABLE `invoice` ADD COLUMN `unit` VARCHAR(20) DEFAULT NULL COMMENT ''单位（选填）'' AFTER `specification`',
    'SELECT 1'
);
PREPARE add_unit_stmt FROM @add_unit_sql;
EXECUTE add_unit_stmt;
DEALLOCATE PREPARE add_unit_stmt;
