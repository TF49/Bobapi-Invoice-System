-- 管理员额度操作幂等键；系统自动扣除记录保持 NULL。
ALTER TABLE `user_quota_transaction`
    ADD COLUMN `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '管理员额度操作幂等键' AFTER `transaction_type`,
    ADD UNIQUE KEY `uk_quota_transaction_user_idempotency` (`user_id`, `idempotency_key`);
