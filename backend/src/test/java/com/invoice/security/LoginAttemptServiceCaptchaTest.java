package com.invoice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceCaptchaTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void requiresCaptchaAfterConfiguredFailures() {
        String key = "1.2.3.4:alice";
        assertThat(service.requiresCaptcha(key)).isFalse();

        service.loginFailed(key);
        assertThat(service.getAttemptCount(key)).isEqualTo(1);
        assertThat(service.requiresCaptcha(key)).isFalse();

        service.loginFailed(key);
        assertThat(service.getAttemptCount(key)).isEqualTo(2);
        assertThat(service.requiresCaptcha(key)).isTrue();

        service.loginSucceeded(key);
        assertThat(service.requiresCaptcha(key)).isFalse();
    }
}
