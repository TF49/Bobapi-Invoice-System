-- V13: 发票表税号字段支持为空并扩展长度
ALTER TABLE invoice
    MODIFY COLUMN tax_number VARCHAR(100) NULL DEFAULT NULL COMMENT '税号（选填）';
