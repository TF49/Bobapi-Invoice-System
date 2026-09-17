-- V20: 新增发票票种字段（普票/专票）
-- NORMAL=普通发票（普票），VAT_SPECIAL=增值税专用发票（专票）
ALTER TABLE invoice
    ADD COLUMN invoice_category VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        COMMENT '发票票种：NORMAL-普票，VAT_SPECIAL-专票';

-- 为存量数据设置默认普票（DEFAULT 已处理，此处为显式更新以便审计）
UPDATE invoice SET invoice_category = 'NORMAL' WHERE invoice_category IS NULL OR invoice_category = '';
