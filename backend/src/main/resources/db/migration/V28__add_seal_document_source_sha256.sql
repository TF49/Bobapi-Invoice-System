-- 记录待盖章原始文件内容的 SHA-256，OpenAPI 上传防重时用于识别"同名但内容不同"的文件。
-- 历史记录保持 NULL，代码中对 NULL 回退为文件名 + 大小比较。
-- 兼容已通过 init.sql 建好该列的数据库。
SET @db_name = DATABASE();

SET @add_column_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'seal_document'
          AND BINARY COLUMN_NAME = BINARY 'source_file_sha256'
    ),
    'ALTER TABLE `seal_document` ADD COLUMN `source_file_sha256` CHAR(64) DEFAULT NULL COMMENT ''原始文件内容 SHA-256（小写十六进制）'' AFTER `source_file_size`',
    'SELECT 1'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;
