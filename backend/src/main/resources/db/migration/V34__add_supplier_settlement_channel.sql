-- V34: 供应商结算按通道（出票主体 × 票种）分类型结算。
-- 新增 seller_company / invoice_category 两列；历史结算记录全部归属「普通公司·普票」(DEFAULT + NORMAL)，由列默认值自动回填。
-- 兼容已通过 init.sql 建好该列的数据库。
SET @db_name = DATABASE();

SET @add_seller_company_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'seller_company'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `seller_company` VARCHAR(20) NOT NULL DEFAULT ''DEFAULT'' COMMENT ''结算通道-出票主体：DEFAULT-普通公司，TECH-科技公司'' AFTER `settlement_amount`',
    'SELECT 1'
);
PREPARE add_seller_company_stmt FROM @add_seller_company_sql;
EXECUTE add_seller_company_stmt;
DEALLOCATE PREPARE add_seller_company_stmt;

SET @add_invoice_category_sql = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE BINARY TABLE_SCHEMA = BINARY @db_name
          AND BINARY TABLE_NAME = BINARY 'supplier_settlement'
          AND BINARY COLUMN_NAME = BINARY 'invoice_category'
    ),
    'ALTER TABLE `supplier_settlement` ADD COLUMN `invoice_category` VARCHAR(20) NOT NULL DEFAULT ''NORMAL'' COMMENT ''结算通道-票种：NORMAL-普通发票，VAT_SPECIAL-增值税专用发票'' AFTER `seller_company`',
    'SELECT 1'
);
PREPARE add_invoice_category_stmt FROM @add_invoice_category_sql;
EXECUTE add_invoice_category_stmt;
DEALLOCATE PREPARE add_invoice_category_stmt;
