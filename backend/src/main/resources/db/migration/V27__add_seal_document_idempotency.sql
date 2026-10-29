-- OpenAPI 上传待盖章文件按 (user_id, idempotency_key) 防重，避免调用方重试导致重复扣费。
-- 网页端上传不传幂等键，保持 NULL（MySQL 唯一索引允许多个 NULL）。
-- 兼容已通过 init.sql 建好该列和索引的数据库。
SET @db_name = DATABASE();

SET @add_column_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'seal_document'
          AND BINARY COLUMN_NAME = BINARY 'idempotency_key'
    ),
    'ALTER TABLE `seal_document` ADD COLUMN `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT ''OpenAPI 上传幂等键'' AFTER `user_id`',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

SET @add_index_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'seal_document'
          AND BINARY INDEX_NAME = BINARY 'uk_sd_user_idempotency'
    ),
    'ALTER TABLE `seal_document` ADD UNIQUE INDEX `uk_sd_user_idempotency` (`user_id`, `idempotency_key`)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
