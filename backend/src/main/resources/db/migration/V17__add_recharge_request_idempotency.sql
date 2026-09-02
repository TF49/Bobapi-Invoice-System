-- 充值申请按用户和幂等键去重，并更正 amount 的字段语义
ALTER TABLE recharge_request
    MODIFY COLUMN amount DECIMAL(10, 2) NOT NULL COMMENT '按手续费计算的申请额度',
    ADD COLUMN idempotency_key VARCHAR(64) NULL COMMENT '用户创建申请的幂等键' AFTER amount,
    ADD UNIQUE KEY uk_recharge_request_user_idempotency (user_id, idempotency_key);
