package com.invoice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
    }

    @Test
    void normalUserLocksAfterFiveFailures() {
        String key = "127.0.0.1:user1";
        assertFalse(loginAttemptService.isLocked(key));

        for (int i = 0; i < 4; i++) {
            loginAttemptService.loginFailed(key);
            assertFalse(loginAttemptService.isLocked(key), "Should not be locked after " + (i + 1) + " failures");
        }

        loginAttemptService.loginFailed(key);
        assertTrue(loginAttemptService.isLocked(key), "Should be locked after 5 failures");
        long remaining = loginAttemptService.getRemainingLockTime(key);
        assertTrue(remaining > 0 && remaining <= 900, "Remaining lock time should be ~15 minutes (900s)");
    }

    @Test
    void adminUserLocksAfterThreeFailures() {
        String key = "127.0.0.1:admin";
        assertFalse(loginAttemptService.isLocked(key));

        loginAttemptService.adminLoginFailed(key);
        assertFalse(loginAttemptService.isLocked(key));

        loginAttemptService.adminLoginFailed(key);
        assertFalse(loginAttemptService.isLocked(key));

        loginAttemptService.adminLoginFailed(key);
        assertTrue(loginAttemptService.isLocked(key), "Admin should be locked after 3 failures");

        long remaining = loginAttemptService.getRemainingLockTime(key);
        assertTrue(remaining > 900 && remaining <= 1800, "Admin lock time should be ~30 minutes (1800s)");
    }

    @Test
    void loginSucceededClearsLockAndAttempts() {
        String key = "127.0.0.1:admin";
        for (int i = 0; i < 3; i++) {
            loginAttemptService.adminLoginFailed(key);
        }
        assertTrue(loginAttemptService.isLocked(key));

        loginAttemptService.loginSucceeded(key);
        assertFalse(loginAttemptService.isLocked(key));
        assertEquals(0, loginAttemptService.getRemainingLockTime(key));
    }
}
