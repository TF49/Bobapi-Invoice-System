-- 使用独立完成时间修正 Dashboard 已开票趋势的日期口径。
-- 兼容通过 init.sql 新建、已经包含 completed_at 的数据库。

SET @completed_at_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'invoice'
      AND column_name = 'completed_at'
);

SET @add_completed_at_sql = IF(
    @completed_at_column_exists = 0,
    'ALTER TABLE `invoice` ADD COLUMN `completed_at` DATETIME NULL COMMENT ''实际完成开票时间'' AFTER `updated_at`',
    'SELECT 1'
);
PREPARE add_completed_at_statement FROM @add_completed_at_sql;
EXECUTE add_completed_at_statement;
DEALLOCATE PREPARE add_completed_at_statement;

-- 历史已完成记录没有独立完成时间，以最后更新时间作为最合理的回填值。
UPDATE `invoice`
SET `completed_at` = `updated_at`
WHERE `status` = 'COMPLETED'
  AND `completed_at` IS NULL;

SET @completed_at_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'invoice'
      AND index_name = 'idx_status_completed_at'
);

SET @add_completed_at_index_sql = IF(
    @completed_at_index_exists = 0,
    'ALTER TABLE `invoice` ADD INDEX `idx_status_completed_at` (`status`, `completed_at`)',
    'SELECT 1'
);
PREPARE add_completed_at_index_statement FROM @add_completed_at_index_sql;
EXECUTE add_completed_at_index_statement;
DEALLOCATE PREPARE add_completed_at_index_statement;
