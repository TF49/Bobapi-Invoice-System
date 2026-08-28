-- V14: 支持 OpenAPI 开发者接入与外部商户单号
-- 1. 用户表增加 API Key 及其启用状态
ALTER TABLE `user`
    ADD COLUMN `api_key` VARCHAR(64) DEFAULT NULL COMMENT 'OpenAPI 调用密钥' AFTER `role`,
    ADD COLUMN `api_key_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'API Key是否启用：1-启用，0-禁用' AFTER `api_key`,
    ADD UNIQUE KEY `uk_user_api_key` (`api_key`);

-- 2. 发票表增加外部商户订单号及唯一索引
ALTER TABLE `invoice`
    ADD COLUMN `out_trade_no` VARCHAR(100) DEFAULT NULL COMMENT '外部商户订单号（用于OpenAPI对接）' AFTER `remark`,
    ADD UNIQUE KEY `uk_invoice_user_out_trade_no` (`user_id`, `out_trade_no`),
    ADD KEY `idx_out_trade_no` (`out_trade_no`);