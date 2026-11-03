package com.invoice.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 内存滑动窗口限流（滑动窗口计数法）。
 *
 * <p>估算值 = 上一窗口计数 × 上一窗口在当前滑动区间内的剩余占比 + 当前窗口计数。
 * 相比固定窗口，可避免在窗口边界处出现 2 倍突发。
 */
@Component
public class RateLimitService {

    /** 计数器数量软上限，超过时立即触发一次过期清理 */
    private static final int SOFT_MAX_KEYS = 200_000;
    private static final long EMERGENCY_CLEAN_INTERVAL_MS = 5_000L;

    private final Map<String, SlidingWindow> counters = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long lastEmergencyCleanAt;

    public RateLimitService() {
        this(System::currentTimeMillis);
    }

    RateLimitService(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * 尝试获取一个许可；未超限时计数 +1。
     */
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        return counter(key, window, now).acquire(now, limit, true);
    }

    /**
     * 仅检查是否已超限，不计数。
     */
    public RateLimitResult check(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        SlidingWindow counter = counters.get(mapKey(key, window));
        if (counter == null) {
            return RateLimitResult.ALLOWED;
        }
        return counter.acquire(now, limit, false);
    }

    /**
     * 无条件记录一次事件（用于失败计数），返回记录后是否已达到上限。
     */
    public RateLimitResult hit(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        SlidingWindow counter = counter(key, window, now);
        counter.record(now);
        return counter.acquire(now, limit, false);
    }

    /**
     * 撤销一次已获取的许可（业务失败时归还配额）。
     */
    public void release(String key, Duration window) {
        SlidingWindow counter = counters.get(mapKey(key, window));
        if (counter != null) {
            counter.release(clock.getAsLong());
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void removeExpiredCounters() {
        long now = clock.getAsLong();
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private SlidingWindow counter(String key, Duration window, long now) {
        if (counters.size() > SOFT_MAX_KEYS && now - lastEmergencyCleanAt > EMERGENCY_CLEAN_INTERVAL_MS) {
            lastEmergencyCleanAt = now;
            removeExpiredCounters();
        }
        long windowMillis = Math.max(1L, window.toMillis());
        return counters.computeIfAbsent(mapKey(key, window), ignored -> new SlidingWindow(windowMillis, now));
    }

    private static String mapKey(String key, Duration window) {
        return key + "|" + window.toMillis();
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
        static final RateLimitResult ALLOWED = new RateLimitResult(true, 0);
    }

    private static final class SlidingWindow {
        private final long windowMillis;
        private long currentStart;
        private int currentCount;
        private int previousCount;
        private long lastAccess;

        private SlidingWindow(long windowMillis, long now) {
            this.windowMillis = windowMillis;
            this.currentStart = now;
            this.lastAccess = now;
        }

        private synchronized RateLimitResult acquire(long now, int limit, boolean consume) {
            roll(now);
            lastAccess = now;
            if (estimate(now) + 1 > limit) {
                return new RateLimitResult(false, retryAfterSeconds(now, limit));
            }
            if (consume) {
                currentCount++;
            }
            return RateLimitResult.ALLOWED;
        }

        private synchronized void record(long now) {
            roll(now);
            lastAccess = now;
            currentCount++;
        }

        private synchronized void release(long now) {
            roll(now);
            if (currentCount > 0) {
                currentCount--;
            }
        }

        private synchronized boolean isExpired(long now) {
            return now - lastAccess > windowMillis * 2;
        }

        private void roll(long now) {
            if (now < currentStart) {
                return;
            }
            long elapsedWindows = (now - currentStart) / windowMillis;
            if (elapsedWindows >= 1) {
                previousCount = elapsedWindows == 1 ? currentCount : 0;
                currentCount = 0;
                currentStart += elapsedWindows * windowMillis;
            }
        }

        private double estimate(long now) {
            double elapsedRatio = Math.min(1.0, (double) Math.max(0, now - currentStart) / windowMillis);
            return previousCount * (1.0 - elapsedRatio) + currentCount;
        }

        /**
         * 计算估算值降到 limit-1 以下所需的等待时间。
         */
        private long retryAfterSeconds(long now, int limit) {
            double allowance = Math.max(0, limit - 1);
            long waitMillis;
            if (limit <= 0) {
                waitMillis = windowMillis;
            } else if (currentCount <= allowance && previousCount > 0) {
                // 当前窗口内等待上一窗口的权重衰减
                double requiredRatio = 1.0 - (allowance - currentCount) / previousCount;
                waitMillis = (long) Math.ceil(currentStart + requiredRatio * windowMillis - now);
            } else {
                // 需要进入下一窗口，并等待本窗口计数的权重衰减
                double requiredRatio = currentCount <= 0 ? 0 : Math.max(0, 1.0 - allowance / currentCount);
                waitMillis = (long) Math.ceil(currentStart + windowMillis + requiredRatio * windowMillis - now);
            }
            waitMillis = Math.max(1, Math.min(waitMillis, windowMillis * 2));
            return Math.max(1, (waitMillis + 999) / 1000);
        }
    }
}
