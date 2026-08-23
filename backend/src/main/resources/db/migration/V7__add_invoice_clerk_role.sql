-- 添加开票员角色支持
-- 修改用户表角色字段注释，明确支持三种角色
ALTER TABLE `user` MODIFY COLUMN `role` VARCHAR(20) NOT NULL COMMENT '角色：USER-普通用户，INVOICE_CLERK-开票员，ADMIN-管理员';