-- Reconcile supplier settlement schema for databases affected by V22's
-- information_schema COLLATE comparisons or partially applied changes.
SET @db_name = DATABASE();

SET @drop_fk_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE BINARY CONSTRAINT_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY CONSTRAINT_NAME = BINARY 'fk_supplier_settlement_user'
          AND BINARY DELETE_RULE <> BINARY 'RESTRICT'
    ),
    'ALTER TABLE `supplier_settlement` DROP FOREIGN KEY `fk_supplier_settlement_user`',
    'SELECT 1'
);
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

SET @add_fk_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE BINARY CONSTRAINT_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY CONSTRAINT_NAME = BINARY 'fk_supplier_settlement_user'
          AND BINARY DELETE_RULE = BINARY 'RESTRICT'
    ),
    'ALTER TABLE `supplier_settlement` ADD CONSTRAINT `fk_supplier_settlement_user` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT',
    'SELECT 1'
);
PREPARE add_fk_stmt FROM @add_fk_sql;
EXECUTE add_fk_stmt;
DEALLOCATE PREPARE add_fk_stmt;

SET @add_column_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'idempotency_key'
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
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'idempotency_key'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY INDEX_NAME = BINARY 'uk_settlement_idempotency'
    ),
    'ALTER TABLE `supplier_settlement` ADD UNIQUE INDEX `uk_settlement_idempotency` (`idempotency_key`)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
