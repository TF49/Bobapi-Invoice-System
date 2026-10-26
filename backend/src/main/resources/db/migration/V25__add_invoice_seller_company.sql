-- V25: 新增发票出票公司主体（普通公司/科技公司）与费率、实际扣除额度
-- seller_company: DEFAULT-普通公司，TECH-科技公司
-- fee_rate: 执行手续费率（如 0.0300, 0.0900, 0.0400, 0.1000）
-- deducted_quota: 本次开票实际扣除的额度，取消或红冲时原路精确退还
ALTER TABLE invoice
    ADD COLUMN seller_company VARCHAR(50) NOT NULL DEFAULT 'DEFAULT'
        COMMENT '出票主体公司：DEFAULT-普通公司，TECH-科技公司',
    ADD COLUMN fee_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.0300
        COMMENT '实际执行费率：普通普票0.03/专票0.09，科技普票0.04/专票0.10',
    ADD COLUMN deducted_quota DECIMAL(12, 2) NOT NULL DEFAULT 0.00
        COMMENT '实际扣除额度（用于发票取消或红冲时精准原样退还）',
    ADD INDEX idx_seller_company (seller_company);

-- 为历史存量发票初始化 deducted_quota 与 fee_rate
UPDATE invoice
SET seller_company = 'DEFAULT',
    fee_rate = CASE WHEN invoice_category = 'VAT_SPECIAL' THEN 0.0900 ELSE 0.0300 END,
    deducted_quota = CASE WHEN invoice_category = 'VAT_SPECIAL' THEN ROUND(amount * 3, 2) ELSE ROUND(amount, 2) END
WHERE deducted_quota = 0.00;
