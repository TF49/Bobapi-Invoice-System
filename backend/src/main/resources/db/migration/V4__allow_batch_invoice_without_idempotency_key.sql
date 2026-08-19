-- 批量申请由 invoice_batch.idempotency_key 保证幂等，明细不占用单条申请的幂等键命名空间。
ALTER TABLE `invoice`
    MODIFY COLUMN `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '单条创建请求幂等键；批量申请明细为空';
