-- V12: 发票表新增用户已处理标记
ALTER TABLE invoice
    ADD COLUMN is_processed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户已处理标记：0-未处理，1-已处理' AFTER status;
