package com.invoice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    @Value("${jwt.remember-me-expiration}")
    private Long rememberMeExpiration;

    private volatile SecretKey signingKey;

    /**
     * 在应用启动阶段校验并初始化签名密钥，避免服务启动后第一次登录才发现配置错误。
     */
    @PostConstruct
    void initializeSigningKey() {
        signingKey = createSigningKey(secret);
    }
    
    /**
     * 生成 token
     */
    public String generateToken(Long userId, String username, String role, Long authVersion) {
        return generateToken(userId, username, role, authVersion, false);
    }
    
    /**
     * 生成 token（支持记住我）
     */
    public String generateToken(Long userId, String username, String role,
                                Long authVersion, Boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("authVersion", authVersion);
        return createToken(claims, username, rememberMe);
    }
    
    /**
     * 创建 token
     */
    private String createToken(Map<String, Object> claims, String subject, Boolean rememberMe) {
        Date now = new Date();
        // 如果记住我，使用更长的有效期
        long expirationTime = (rememberMe != null && rememberMe) ? rememberMeExpiration : expiration;
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }
    
    /**
     * 从 token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }
    
    /**
     * 从 token 中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }
    
    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getAuthVersionFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("authVersion", Long.class);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 从 token 中获取 Claims
     */
    private Claims getClaimsFromToken(String token) {
        return parseClaims(token);
    }
    
    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        SecretKey currentKey = signingKey;
        // 兼容不经过 Spring 容器直接实例化 JwtUtil 的单元测试。
        if (currentKey == null) {
            synchronized (this) {
                currentKey = signingKey;
                if (currentKey == null) {
                    currentKey = createSigningKey(secret);
                    signingKey = currentKey;
                }
            }
        }
        return currentKey;
    }

    private SecretKey createSigningKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 不能为空，请配置至少 32 字节的随机密钥");
        }

        byte[] keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 32 字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
