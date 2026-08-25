-- V10: 用户表新增管理员备注字段
ALTER TABLE `user`
    ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '管理员备注' AFTER `role`;
