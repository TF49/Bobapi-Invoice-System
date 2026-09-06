-- 使用条件 DDL 兼容曾执行过旧 V18 发票来源迁移的数据库，避免重复 ADD COLUMN 失败。
SET @submission_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'invoice'
      AND COLUMN_NAME = 'submission_type'
);
SET @add_submission_type = IF(
    @submission_type_exists = 0,
    'ALTER TABLE invoice ADD COLUMN submission_type VARCHAR(20) NULL COMMENT ''提交方式：API-API提交，MANUAL-网页手动提交，UNKNOWN-历史无法确认'' AFTER batch_row_number',
    'SELECT 1'
);
PREPARE add_submission_type_statement FROM @add_submission_type;
EXECUTE add_submission_type_statement;
DEALLOCATE PREPARE add_submission_type_statement;

-- 回填可以可靠识别的历史记录：开放 API 单条申请带有外部订单号或 open_ 前缀幂等键；
-- 网页端申请带有 invoice- 前缀幂等键；批次通过批次幂等键（open_batch_ / batch- / invoice-）识别来源。
-- 自定义 API 幂等键以及旧数据中没有可靠标识的记录保留 UNKNOWN，避免错误归类。
UPDATE invoice
SET submission_type = CASE
    WHEN submission_type IN ('API', 'MANUAL') THEN submission_type
    WHEN out_trade_no IS NOT NULL THEN 'API'
    WHEN LEFT(idempotency_key, 5) = 'open_' THEN 'API'
    WHEN batch_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM invoice_batch b
        WHERE b.id = invoice.batch_id AND (LEFT(b.idempotency_key, 11) = 'open_batch_' OR LEFT(b.idempotency_key, 5) = 'open_')
    ) THEN 'API'
    WHEN batch_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM invoice_batch b
        WHERE b.id = invoice.batch_id AND (b.idempotency_key LIKE 'batch-%' OR b.idempotency_key LIKE 'invoice-%')
    ) THEN 'MANUAL'
    WHEN batch_id IS NOT NULL THEN 'UNKNOWN'
    WHEN idempotency_key LIKE 'invoice-%' THEN 'MANUAL'
    ELSE 'UNKNOWN'
END
WHERE submission_type IS NULL OR submission_type = 'UNKNOWN' OR submission_type = '';

ALTER TABLE invoice
    MODIFY COLUMN submission_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '提交方式：API-API提交，MANUAL-网页手动提交，UNKNOWN-历史无法确认';
