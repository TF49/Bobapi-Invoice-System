-- Ensure supplier settlement idempotency support exists in databases where V22
-- was recorded as applied before the column/index was actually present.
SET @db_name = DATABASE();

SET @add_column_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'supplier_settlement'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'supplier_settlement'
          AND COLUMN_NAME = 'idempotency_key'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT ''结算请求幂等键'' AFTER `operator_name`',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

SET @add_index_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'supplier_settlement'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'supplier_settlement'
          AND COLUMN_NAME = 'idempotency_key'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'supplier_settlement'
          AND INDEX_NAME = 'uk_settlement_idempotency'
    ),
    'ALTER TABLE `supplier_settlement` ADD UNIQUE INDEX `uk_settlement_idempotency` (`idempotency_key`)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
