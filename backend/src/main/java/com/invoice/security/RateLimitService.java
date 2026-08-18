package com.invoice.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitService {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        return counter.tryAcquire(now, limit, window.toMillis());
    }

    @Scheduled(fixedRate = 600_000)
    public void removeExpiredCounters() {
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(20).toMillis();
        counters.entrySet().removeIf(entry -> entry.getValue().lastAccess() < cutoff);
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }

    private static final class WindowCounter {
        private long windowStartedAt;
        private long lastAccess;
        private int count;

        private WindowCounter(long now) {
            this.windowStartedAt = now;
            this.lastAccess = now;
        }

        private synchronized RateLimitResult tryAcquire(long now, int limit, long windowMillis) {
            lastAccess = now;
            if (now - windowStartedAt >= windowMillis) {
                windowStartedAt = now;
                count = 0;
            }
            if (count >= limit) {
                long remainingMillis = Math.max(1, windowMillis - (now - windowStartedAt));
                return new RateLimitResult(false, Math.max(1, (remainingMillis + 999) / 1000));
            }
            count++;
            return new RateLimitResult(true, 0);
        }

        private synchronized long lastAccess() {
            return lastAccess;
        }
    }
}
