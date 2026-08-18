CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户，ADMIN-管理员',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号状态：1-启用，0-禁用',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '认证版本，变更后使旧凭证失效',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_username` (`username`),
    INDEX `idx_role` (`role`),
    INDEX `idx_user_admin_state` (`role`, `enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

SET @has_enabled = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'enabled'
);
SET @add_enabled = IF(
    @has_enabled = 0,
    'ALTER TABLE `user` ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''账号状态：1-启用，0-禁用'' AFTER `role`',
    'SELECT 1'
);
PREPARE add_enabled_statement FROM @add_enabled;
EXECUTE add_enabled_statement;
DEALLOCATE PREPARE add_enabled_statement;

SET @has_auth_version = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'auth_version'
);
SET @add_auth_version = IF(
    @has_auth_version = 0,
    'ALTER TABLE `user` ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT ''认证版本，变更后使旧凭证失效'' AFTER `enabled`',
    'SELECT 1'
);
PREPARE add_auth_version_statement FROM @add_auth_version;
EXECUTE add_auth_version_statement;
DEALLOCATE PREPARE add_auth_version_statement;

SET @has_admin_state_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_admin_state'
);
SET @add_admin_state_index = IF(
    @has_admin_state_index = 0,
    'CREATE INDEX `idx_user_admin_state` ON `user` (`role`, `enabled`, `deleted`)',
    'SELECT 1'
);
PREPARE add_admin_state_index_statement FROM @add_admin_state_index;
EXECUTE add_admin_state_index_statement;
DEALLOCATE PREPARE add_admin_state_index_statement;

CREATE TABLE IF NOT EXISTS `invoice` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发票ID',
    `company_name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `tax_number` VARCHAR(20) NOT NULL COMMENT '税号',
    `amount` DECIMAL(12, 2) NOT NULL COMMENT '开票金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待开票，COMPLETED-已开票',
    `file_path` VARCHAR(500) DEFAULT NULL COMMENT '服务端存储文件名',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始发票文件名',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '创建请求幂等键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    UNIQUE INDEX `uk_invoice_user_idempotency` (`user_id`, `idempotency_key`),
    CONSTRAINT `fk_invoice_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票表';

INSERT IGNORE INTO `user` (`username`, `password`, `role`) VALUES
('admin', '$2a$10$MEaAb2ukAOIvQ2bBf5lhquRLTEbIz5OAa79pcbQQ3EXufSHL2mvNS', 'ADMIN'),
('user', '$2a$10$8ORsuwbOGeGgarcH2nik8uvA1c8X4ah98zVMgpkkwIg.6PImYIRZ2', 'USER');
