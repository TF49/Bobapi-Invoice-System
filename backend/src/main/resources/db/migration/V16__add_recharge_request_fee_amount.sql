-- 保存用户实际支付的手续费；旧申请无法可靠反推，因此保持为 NULL
ALTER TABLE recharge_request
    ADD COLUMN fee_amount DECIMAL(10, 2) NULL COMMENT '用户支付的手续费' AFTER user_id;
