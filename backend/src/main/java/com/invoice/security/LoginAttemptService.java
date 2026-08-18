package com.invoice.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限制器
 * 使用内存存储登录失败记录，5次失败锁定15分钟
 */
@Component
public class LoginAttemptService {
    
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15 * 60 * 1000; // 15分钟
    
    // 存储登录失败次数：key=IP或用户名, value=失败次数
    private final ConcurrentHashMap<String, Integer> attemptsCache = new ConcurrentHashMap<>();
    
    // 存储锁定时间：key=IP或用户名, value=锁定时间戳
    private final ConcurrentHashMap<String, Long> lockCache = new ConcurrentHashMap<>();
    
    /**
     * 登录失败，增加失败次数
     */
    public void loginFailed(String key) {
        int attempts = attemptsCache.getOrDefault(key, 0) + 1;
        attemptsCache.put(key, attempts);
        
        // 如果达到最大失败次数，锁定账户
        if (attempts >= MAX_ATTEMPTS) {
            lockCache.put(key, System.currentTimeMillis());
        }
    }
    
    /**
     * 登录成功，清除失败记录
     */
    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
        lockCache.remove(key);
    }
    
    /**
     * 检查是否被锁定
     */
    public boolean isLocked(String key) {
        Long lockTime = lockCache.get(key);
        if (lockTime == null) {
            return false;
        }
        
        // 检查锁定时间是否已过
        if (System.currentTimeMillis() - lockTime > LOCK_TIME_DURATION) {
            // 锁定时间已过，解除锁定
            lockCache.remove(key);
            attemptsCache.remove(key);
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取剩余锁定时间（秒）
     */
    public long getRemainingLockTime(String key) {
        Long lockTime = lockCache.get(key);
        if (lockTime == null) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - lockTime;
        long remaining = LOCK_TIME_DURATION - elapsed;
        return remaining > 0 ? remaining / 1000 : 0;
    }
}