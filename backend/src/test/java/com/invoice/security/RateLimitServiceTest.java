package com.invoice.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    @Test
    void blocksRequestsAfterTheConfiguredLimit() {
        RateLimitService service = new RateLimitService();

        for (int index = 0; index < 10; index++) {
            assertThat(service.tryAcquire("login:test", 10, Duration.ofMinutes(1)).allowed()).isTrue();
        }

        RateLimitService.RateLimitResult blocked =
                service.tryAcquire("login:test", 10, Duration.ofMinutes(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void tracksKeysIndependently() {
        RateLimitService service = new RateLimitService();

        assertThat(service.tryAcquire("user:1", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(service.tryAcquire("user:1", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(service.tryAcquire("user:2", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
