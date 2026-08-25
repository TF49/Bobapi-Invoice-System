-- 更新管理员默认密码（高强度密码，BCrypt 加密）
UPDATE `user` SET `password` = '$2b$10$03QLUNY7Yr9xBWJwbugxdONqzssH03qsdhG2XBiG9.RxHQjniRkTG' WHERE `username` = 'admin';
