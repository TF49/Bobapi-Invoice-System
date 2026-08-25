-- 充值申请表
CREATE TABLE IF NOT EXISTS recharge_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount DECIMAL(10, 2) NOT NULL COMMENT '申请充值金额',
    screenshot_url VARCHAR(512) COMMENT '充值截图URL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '申请状态：PENDING-待审核，APPROVED-已通过，REJECTED-已拒绝',
    remark VARCHAR(500) COMMENT '用户备注',
    admin_remark VARCHAR(500) COMMENT '管理员审核备注',
    reviewed_by BIGINT COMMENT '审核管理员ID',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值申请表';