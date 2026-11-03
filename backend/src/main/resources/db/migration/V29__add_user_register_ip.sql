-- V29: 记录自助注册来源 IP，便于识别与清理批量注册的账号
-- 兼容已通过 init.sql 建好该列/索引的数据库。
SET @db_name = DATABASE();

SET @add_column_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'user'
          AND BINARY COLUMN_NAME = BINARY 'register_ip'
    ),
    'ALTER TABLE `user` ADD COLUMN `register_ip` VARCHAR(45) DEFAULT NULL COMMENT ''自助注册来源 IP（管理员创建为空）'' AFTER `remark`',
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
          AND BINARY TABLE_NAME = BINARY 'user'
          AND BINARY INDEX_NAME = BINARY 'idx_user_register_ip'
    ),
    'ALTER TABLE `user` ADD INDEX `idx_user_register_ip` (`register_ip`)',
    'SELECT 1'
);
PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
