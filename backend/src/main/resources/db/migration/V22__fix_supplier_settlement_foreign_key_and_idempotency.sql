-- 修复供应商结算表的外键约束并增加幂等键。
-- 使用动态 SQL 保证已手工执行过部分变更的环境仍可完成迁移。
SET @db_name = DATABASE();

SET @drop_fk_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @db_name COLLATE utf8mb4_unicode_ci
          AND TABLE_NAME = 'supplier_settlement' COLLATE utf8mb4_unicode_ci
          AND CONSTRAINT_NAME = 'fk_supplier_settlement_user' COLLATE utf8mb4_unicode_ci
          AND DELETE_RULE <> 'RESTRICT' COLLATE utf8mb4_unicode_ci
    ),
    'ALTER TABLE `supplier_settlement` DROP FOREIGN KEY `fk_supplier_settlement_user`',
    'SELECT 1'
);
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

SET @add_fk_sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @db_name COLLATE utf8mb4_unicode_ci
          AND TABLE_NAME = 'supplier_settlement' COLLATE utf8mb4_unicode_ci
          AND CONSTRAINT_NAME = 'fk_supplier_settlement_user' COLLATE utf8mb4_unicode_ci
          AND DELETE_RULE = 'RESTRICT' COLLATE utf8mb4_unicode_ci
    ),
    'ALTER TABLE `supplier_settlement` ADD CONSTRAINT `fk_supplier_settlement_user` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT',
    'SELECT 1'
);
PREPARE add_fk_stmt FROM @add_fk_sql;
EXECUTE add_fk_stmt;
DEALLOCATE PREPARE add_fk_stmt;

SET @add_column_sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name COLLATE utf8mb4_unicode_ci
          AND TABLE_NAME = 'supplier_settlement' COLLATE utf8mb4_unicode_ci
          AND COLUMN_NAME = 'idempotency_key' COLLATE utf8mb4_unicode_ci
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT ''结算请求幂等键'' AFTER `operator_name`',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

SET @add_index_sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @db_name COLLATE utf8mb4_unicode_ci
          AND TABLE_NAME = 'supplier_settlement' COLLATE utf8mb4_unicode_ci
          AND INDEX_NAME = 'uk_settlement_idempotency' COLLATE utf8mb4_unicode_ci
    ),
    'ALTER TABLE `supplier_settlement` ADD UNIQUE INDEX `uk_settlement_idempotency` (`idempotency_key`)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
