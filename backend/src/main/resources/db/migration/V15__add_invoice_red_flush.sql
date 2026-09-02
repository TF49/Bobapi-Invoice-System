-- V15: 支持发票红冲流程
ALTER TABLE `invoice`
    ADD COLUMN `red_flush_status` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '红冲状态：NONE-未申请，PENDING-待红冲，COMPLETED-已红冲，REJECTED-已驳回' AFTER `is_processed`,
    ADD COLUMN `red_flush_reason` VARCHAR(500) DEFAULT NULL COMMENT '用户申请红冲原因' AFTER `red_flush_status`,
    ADD COLUMN `red_flush_remark` VARCHAR(500) DEFAULT NULL COMMENT '开票员/管理员处理备注' AFTER `red_flush_reason`,
    ADD COLUMN `red_flush_apply_time` DATETIME DEFAULT NULL COMMENT '红冲申请时间' AFTER `red_flush_remark`,
    ADD COLUMN `red_flush_complete_time` DATETIME DEFAULT NULL COMMENT '红冲完成时间' AFTER `red_flush_apply_time`,
    ADD COLUMN `red_flush_operator_id` BIGINT DEFAULT NULL COMMENT '红冲操作人ID' AFTER `red_flush_complete_time`,
    ADD KEY `idx_red_flush_status` (`red_flush_status`);
