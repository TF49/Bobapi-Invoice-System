package com.invoice.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        String token = jwtUtil.generateToken(14L, "admin", "ADMIN", 1L);
        assertNotNull(token);
        assertEquals(14L, jwtUtil.getUserIdFromToken(token));
        assertEquals(1L, jwtUtil.getAuthVersionFromToken(token));
    }

    @Test
    void getLongClaimSafelyHandlesIntegerAndLong() {
        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.claims()
                .add("intVal", 14)
                .add("longVal", 100L)
                .add("strVal", "abc")
                .build();

        assertEquals(14L, JwtUtil.getLongClaim(claims, "intVal"));
        assertEquals(100L, JwtUtil.getLongClaim(claims, "longVal"));
        assertNull(JwtUtil.getLongClaim(claims, "strVal"));
        assertNull(JwtUtil.getLongClaim(claims, "nonExistent"));
    }
}
