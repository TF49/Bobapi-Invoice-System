package com.invoice.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void rejectsSecretShorterThanHmacMinimumAtInitialization() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "too-short");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                jwtUtil::initializeSigningKey);

        assertTrue(exception.getMessage().contains("至少需要 32 字节"));
    }

    @Test
    void initializesSigningKeyBeforeFirstTokenIsGenerated() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "TestOnlyInvoiceJwtSecretThatIsLongEnoughForHS256Signing!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "rememberMeExpiration", 60_000L);
        jwtUtil.initializeSigningKey();

        assertNotNull(jwtUtil.generateToken(1L, "admin", "ADMIN", 1L));
    }
}
