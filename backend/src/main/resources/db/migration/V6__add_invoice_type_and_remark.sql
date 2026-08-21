-- V6: 发票表新增开票类型和备注字段
ALTER TABLE invoice
    ADD COLUMN invoice_type VARCHAR(100) NOT NULL DEFAULT '技术服务费' COMMENT '开票类型' AFTER amount,
    ADD COLUMN remark       VARCHAR(500)          DEFAULT NULL         COMMENT '备注'        AFTER invoice_type;
