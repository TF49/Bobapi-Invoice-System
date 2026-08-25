package com.invoice.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限制器
 * 默认普通用户：5次失败锁定15分钟
 * 管理员用户：3次失败锁定30分钟
 */
@Component
public class LoginAttemptService {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long DEFAULT_LOCK_DURATION_MS = 15 * 60 * 1000L; // 15分钟

    public static final int ADMIN_MAX_ATTEMPTS = 3;
    public static final long ADMIN_LOCK_DURATION_MS = 30 * 60 * 1000L; // 30分钟

    private static final class AttemptRecord {
        private final int count;
        private final long lastAttemptTime;

        private AttemptRecord(int count, long lastAttemptTime) {
            this.count = count;
            this.lastAttemptTime = lastAttemptTime;
        }
    }

    // 存储登录失败记录：key=客户端标识, value=AttemptRecord
    private final ConcurrentHashMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    // 存储锁定截止时间戳：key=客户端标识, value=锁定截止毫秒时间戳
    private final ConcurrentHashMap<String, Long> lockUntilCache = new ConcurrentHashMap<>();

    /**
     * 普通用户登录失败，增加失败次数（5次失败锁定15分钟）
     */
    public void loginFailed(String key) {
        loginFailed(key, DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCK_DURATION_MS);
    }

    /**
     * 管理员登录失败，增加失败次数（3次失败锁定30分钟）
     */
    public void adminLoginFailed(String key) {
        loginFailed(key, ADMIN_MAX_ATTEMPTS, ADMIN_LOCK_DURATION_MS);
    }

    /**
     * 指定最大尝试次数和锁定时长的登录失败处理
     */
    public void loginFailed(String key, int maxAttempts, long lockDurationMillis) {
        long now = System.currentTimeMillis();
        AttemptRecord record = attemptsCache.compute(key, (k, oldRecord) -> {
            if (oldRecord == null) {
                return new AttemptRecord(1, now);
            }
            // 如果上次失败距离现在已经超过锁定时长，则重置计数
            if (now - oldRecord.lastAttemptTime > lockDurationMillis) {
                return new AttemptRecord(1, now);
            }
            return new AttemptRecord(oldRecord.count + 1, now);
        });

        if (record != null && record.count >= maxAttempts) {
            lockUntilCache.put(key, now + lockDurationMillis);
        }
    }

    /**
     * 登录成功，清除失败记录和锁定状态
     */
    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
        lockUntilCache.remove(key);
    }

    /**
     * 检查是否被锁定
     */
    public boolean isLocked(String key) {
        Long lockUntil = lockUntilCache.get(key);
        if (lockUntil == null) {
            return false;
        }

        if (System.currentTimeMillis() >= lockUntil) {
            lockUntilCache.remove(key);
            attemptsCache.remove(key);
            return false;
        }

        return true;
    }

    /**
     * 获取剩余锁定时间（秒）
     */
    public long getRemainingLockTime(String key) {
        Long lockUntil = lockUntilCache.get(key);
        if (lockUntil == null) {
            return 0;
        }

        long remaining = lockUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000 : 0;
    }

    /**
     * 定期清理过期的锁定记录和超时的尝试记录（每10分钟执行一次）
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanExpiredRecords() {
        long now = System.currentTimeMillis();
        lockUntilCache.entrySet().removeIf(entry -> now >= entry.getValue());
        attemptsCache.entrySet().removeIf(entry -> (now - entry.getValue().lastAttemptTime) > ADMIN_LOCK_DURATION_MS);
    }
}